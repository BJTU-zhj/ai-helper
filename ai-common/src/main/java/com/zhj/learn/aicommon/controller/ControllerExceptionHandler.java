package com.zhj.learn.aicommon.controller;


import cn.hutool.core.util.StrUtil;

import com.zhj.learn.aicommon.VO.CommonResp;
import com.zhj.learn.aicommon.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@ControllerAdvice
public class ControllerExceptionHandler {

    private static final Logger LOG= LoggerFactory.getLogger(ControllerExceptionHandler.class);

    /**
     * 统一异常处理
     * @param e
     * @return
     */


    //自定义异常处理
    @ExceptionHandler(value = BusinessException.class)
    @ResponseBody
    public CommonResp exceptionHandler(BusinessException e){
        CommonResp commonResp = new CommonResp();
        commonResp.setSuccess(false);
        commonResp.setMessage(e.getBusinessExceptionEnum().getDesc());
        LOG.error("业务异常 {}",e.getBusinessExceptionEnum().getDesc());
        return commonResp;
    }

    //参数校验异常
    @ExceptionHandler(value = MethodArgumentNotValidException .class)
    @ResponseBody
    public CommonResp exceptionHandler(MethodArgumentNotValidException e){
        CommonResp commonResp = new CommonResp();
        // 获取所有的校验错误信息
        // e.getBindingResult().getAllErrors() 会返回所有没通过校验的字段错误
        List<ObjectError> allErrors = e.getBindingResult().getAllErrors();

        // 1. 拼接所有的错误信息用于日志记录
        StringBuilder logMessage = new StringBuilder("参数校验失败：");
        for (ObjectError error : allErrors) {
            logMessage.append("[").append(error.getDefaultMessage()).append("] ");
        }

        // 2. 记录错误日志
        LOG.error("业务异常：{}", logMessage.toString());
        commonResp.setSuccess(false);
        commonResp.setMessage(allErrors.get(0).getDefaultMessage());
        return commonResp;
    }

}
