package com.marshal.halcyon.workflow.leavebill.controller;

import com.marshal.halcyon.core.entity.ResponseData;
import com.marshal.halcyon.core.util.ResponseUtil;
import com.marshal.halcyon.workflow.leavebill.entity.ActBizLeave;
import com.marshal.halcyon.workflow.leavebill.service.LeaveBillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @auth: Marshal
 * @date: 2019/2/8
 * @desc:
 */
@RestController
@RequestMapping("/workflow/leaveBill")
public class LeaveBillController {

    @Autowired
    LeaveBillService leaveBillService;

    @RequestMapping("/createNewInstance")
    public ResponseData createLeaveBillInstance(@RequestBody ActBizLeave actBizLeave) {
        actBizLeave.setUserCode("Marshal");
        try {
            leaveBillService.createLeaveBillInstance(actBizLeave);
        } catch (Exception e) {
            return ResponseUtil.responseErr();
        }
        return ResponseUtil.responseOk("申请成功!");
    }

    @RequestMapping("/queryById/{id}")
    public ActBizLeave queryById(@PathVariable Long id) {
        return leaveBillService.queryById(id);
    }
}
