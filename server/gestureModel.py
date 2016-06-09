import _init_paths
from fast_rcnn.test import *
import time
import Queue

class gestDetModel:
    def __init__(self, proto, mdl, thresh_nms, thresh_conf, classes):
        self.net = caffe.Net(proto, mdl, caffe.TEST)
        self.NMS_THRESH = thresh_nms
        self.CONF_THRESH = thresh_conf
        self.CLASSES = ('__background__', ) + classes

    def detect(self, im):

        blobs = {'data': None, 'rois': None}

        im_orig = im.astype(np.float32, copy=True)
        im_orig -= cfg.PIXEL_MEANS

        im_shape = im_orig.shape
        im_size_min = np.min(im_shape[0:2])
        im_size_max = np.max(im_shape[0:2])

        im_scale = float(600) / float(im_size_min)

        if np.round(im_scale * im_size_max) > 1000:
            im_scale = float(1000) / float(im_size_max)

        im = cv2.resize(im_orig, None, None, fx=im_scale, fy=im_scale, interpolation=cv2.INTER_AREA)
        print 'after scaling: ', im.shape

        blobs['data'] = im_list_to_blob([im])
        im_scales = np.array([im_scale])

        im_blob = blobs['data']
        blobs['im_info'] = np.array([[im_blob.shape[2], im_blob.shape[3], im_scales[0]]], dtype=np.float32)

        self.net.blobs['data'].reshape(*(blobs['data'].shape))
        self.net.blobs['im_info'].reshape(*(blobs['im_info'].shape))

        forward_kwargs = {'data': blobs['data'].astype(np.float32, copy=False), 'im_info': blobs['im_info'].astype(np.float32, copy=False)}

        blobs_out = self.net.forward(**forward_kwargs)
        print 'prediction done'

        rois = self.net.blobs['rois'].data.copy()
        boxes = rois[:, 1:5] / im_scale
        scores = blobs_out['cls_prob']
        box_deltas = blobs_out['bbox_pred']

        pred_boxes = bbox_transform_inv(boxes, box_deltas)
        pred_boxes = clip_boxes(pred_boxes, im_shape)

        handbboxs = np.zeros((0, 6))
        for cls_ind, _ in enumerate(self.CLASSES[1:]):
            cls_ind += 1
            cls_boxes = pred_boxes[:, 4*cls_ind:4*(cls_ind+1)]
            cls_scores = scores[:, cls_ind]
            dets = np.hstack((cls_boxes, cls_scores[:, np.newaxis])).astype(np.float32)
            keep = nms(dets, self.NMS_THRESH)
            dets = dets[keep, :]

            inds = np.where(dets[:, -1] >= self.CONF_THRESH)[0]
            #  print 'filtering done'

            if len(inds):
                bboxs = dets[inds, :4]
                confs = dets[inds, -1][:, np.newaxis]
                bboxs = bboxs.astype(int)
                handbboxs = np.hstack((bboxs, confs, np.ones(confs.shape) * cls_ind)) if not len(handbboxs) else np.vstack((handbboxs, np.hstack((bboxs, confs, np.ones(confs.shape) * cls_ind))))

        return handbboxs

    def serv(self, inp_q, res_q):
        while True:
            try:
                cli_name, frm_buffer = inp_q.get(timeout=2)
                nparr = np.fromstring(frm_buffer, dtype=np.uint8)
                img_np = cv2.imdecode(nparr, cv2.CV_LOAD_IMAGE_COLOR)
                res_q.put((cli_name, self.detect(img_np)))
                inp_q.task_done()
            except (Queue.Empty, KeyboardInterrupt):
                #  print 'timeout in gesture'
                pass



