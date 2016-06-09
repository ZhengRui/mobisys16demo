#!/usr/bin/env python

from gestureModel import gestDetModel

import socket
import threading
from multiprocessing import Process, JoinableQueue, Event, Value
import Queue
from multiprocessing.dummy import Process as DummyProcess
import SocketServer as SS
import signal
import struct
import numpy as np
import math
import cv2
import sys
import os
import time
import caffe


class RequestHandler(SS.BaseRequestHandler):

    #  To subclass, check exmaple:
    #  http://bioportal.weizmann.ac.il/course/python/PyMOTW/PyMOTW/docs/SocketServer/index.html

    def __init__(self, request, client_address, server):
        self.name = threading.currentThread().getName()
        self.res = {}
        skt_clients_map[self.name] = self
        SS.BaseRequestHandler.__init__(self, request, client_address, server)
        return

    def handle(self):
        print "tcp request received, create client ", self.name

        # TODO: receive data size and unpack according to it
        # | header (size_frm) | frm_data |
        headerBytes = self.request.recv(4)
        size = struct.unpack('1i', headerBytes)
        print size

        self.res={'hand_res':None, 'hand_res_evt':Event()}

        size_recv = 0
        frm_buffer = ''

        while size_recv < size:
            chunk = self.request.recv(size - size_recv)
            if not chunk:
                print "socket receiving error"
                return None
            else:
                frm_buffer += chunk
                size_recv += len(chunk)
                #  print "size, size_recv", size, size_recv

        print "Frame received"

        # start gesture detection
        # waiting for result
        hand_inp_q.put((self.name, frm_buffer))
        self.res['hand_res_evt'].wait()
            
        print "hand result : ", self.res['hand_res'][:, -2:]


#        # Case 1: gesture "no" --> blurring
#        # Case 2: gesture "yes" --> not blurring 
#        # Case 3: tag alone --> blurring
#    
#        # find nearest face for each yes or no gesture
#        gesture_user = {2:[], 3:[]}
#        tag_user = []
#
#        face_centers = np.zeros((len(face_num), 2))
#        for i in range(len(face_num)):
#            center = face_bbx[i].center()
#            face_centers[i, :] = [center.x, center.y]
#
#        for (x0, y0, x1, y1, score, hand_cls) in self.res['hand_res']:
#            if hand_cls != 1: # not for natural hand
#                hand_center = np.array([(x0 + x1) / 2, (y0 + y1) / 2])
#                dis_centers = np.linalg.norm(face_centers-hand_center, ord=2, axis=1)
#                gesture_user[hand_cls].append(np.argmin(dis_centers))
#
#        # find nearest face for each tag
#        tag_centers = np.zeros((len(tag_num), 2))
#        for i in range(len(tag_num)):
#            tag_center = tag_bbx[i].center()
#            dis_centers = np.linalg.norm(face_centers-tag_center, ord=2, axis=1)
#            tag_user.append(np.argmin(dis_centers))            
#
#        for i in range(len(face_num)):
#            # 'no' gesture is used
#            if i in gesture_user[3]:
#                print "Case 1: gesture 'no' --> blurring"
#                self.res['c1'].append(i)
#
#            # 'no' gesture is used
#            elif i in gesture_user[2]:
#                print "Case 2: gesture 'yes' --> not blurring"
#                self.res['c2'].append(i)
#
#            # no hand gesture is detected
#            elif i in tag_user:
#                print "Case 3: tag alone --> blurring"
#                self.res['c3'].append(i)

        # send message back to the client
        # header (hand_size) | hand_data
        
        # pack all hands
        # 4i (rectangles) | 1i (hand class) | 1f (score)
        hand_data = ''
        data_to_send = ''

        for x0, y0, x1, y1, scr, hand_cls in self.res['hand_res']:
            data = struct.pack('5i1f', int(x0), int(y0), int(x1), int(y1), int(hand_cls), scr)
            hand_data += data

        hand_size = struct.pack('1i', len(hand_data))
        data_to_send = hand_size + hand_data
        
        self.request.send(data_to_send)         

    def finish(self):
        skt_clients_map.pop(self.name)
        print "tcp request finished"
        return SS.BaseRequestHandler.finish(self)

class Server(SS.ThreadingMixIn, SS.TCPServer):
    pass

def modelPrepare(model_class_name, params, tsk_queues):
    #  caffe initialization has to be put here, otherwise encounter:
       #  "syncedmem.cpp: error == cudaSuccess (3 vs. 0)"
    #  or
       #  "math_functions.cu:28: CUBLAS_STATUS_SUCCESS (14 vs. 0) CUBLAS_STATUS_INTERNAL_ERROR"
    caffe.set_mode_cpu()
    modelClass = getattr(sys.modules[__name__], model_class_name)
    model = modelClass(*params)
    model.serv(*tsk_queues)

def resMailMan(res_q, jobtype): # jobtype is 'hand_res'
    while dummycontinue:
        try:
            cli_name, res = res_q.get(timeout=2)
            client = skt_clients_map[cli_name]
            client.res[jobtype] = res
            client.res[jobtype + '_evt'].set()
            #  print cli_name, " ", jobtype, " result mailed"
        except Queue.Empty:
            #  print 'timeout in ', jobtype
            if jobtype == 'face_res':
                faceres_empty_evt.set()
            pass

def transRec(dlib_rec):
    bbs_x0 = dlib_rec.left()
    bbs_y0 = dlib_rec.top()
    bbs_x1 = dlib_rec.right()
    bbs_y1 = dlib_rec.bottom()
    return bbs_x0, bbs_y0, bbs_x1, bbs_y1

def fillString(s, l):
    ext = l - len(s)
    return s[:l] if ext < 0 else (s + ' ' * ext)



if __name__ == "__main__":

    import wingdbstub
    if 'WINGDB_ACTIVE' in os.environ:
        print "Success starting debug"
    else:
        print "Failed to start debug... Continuing without debug"


    # input_q & res_q are JoinableQueue with put() and get(): put() in handle(), task_done() in gestureModel, get() in resMailMan().
    # Event() is for communication among process/thread: wait() in handle() to wait, set() in resMailMan.
    # worker_hand is a Process calls modelPrepare() for gesture detection.
    # worker_handres_mailman is a DummyProcess calls resMainMan() for results informing.
    

    skt_clients_map = {}

    hand_inp_q = JoinableQueue()
    hand_res_q = JoinableQueue()

    worker_hand_p1 = Process(target = modelPrepare, args = ('gestDetModel',
    ("/Users/Jiayu/Documents/Model/VGG_HAND/test.prototxt",
     "/Users/Jiayu/Documents/Model/VGG_HAND/vgg16_faster_rcnn_handGesdet_aug_fulldata_iter_50000.caffemodel",
     0.4, 0.8, ('natural', 'yes', 'no')), (hand_inp_q, hand_res_q)))

    # worker_hand_p2 = Process(target = modelPrepare, args = ('gestDetModel',
    # ("/home/zerry/Work/Libs/py-faster-rcnn/models/VGG16/faster_rcnn_end2end_handGesdet/test.prototxt",
    #  "/home/zerry/Work/Libs/py-faster-rcnn/output/faster_rcnn_end2end_handGesdet/trainval/vgg16_faster_rcnn_handGesdet_aug_fulldata_iter_50000.caffemodel",
    #  0.4, 0.8, ('natural', 'yes', 'no')), (hand_inp_q, hand_res_q)))

    dummycontinue = True
    worker_handres_mailman = DummyProcess(target = resMailMan, args = (hand_res_q, 'hand_res'))
    worker_hand_p1.daemon = True
    # worker_hand_p2.daemon = True
    worker_handres_mailman.daemon = True

    worker_hand_p1.start()
    # worker_hand_p2.start()
    worker_handres_mailman.start()

    HOST, PORT = "", 9999
    server = Server((HOST, PORT), RequestHandler)
    ip, port = server.server_address
    server_thread = threading.Thread(target=server.serve_forever)
    server_thread.daemon = True
    server_thread.start()

    try:
        signal.pause()
    except:
        server.shutdown()
        server.server_close()
        worker_hand_p1.terminate()
        #  worker_hand_p2.terminate()

        worker_hand_p1.join()
        #  worker_hand_p2.join()

        dummycontinue = False
        worker_handres_mailman.join()
