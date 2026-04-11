package com.zhj.learn.aicommon.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class BusinessException extends RuntimeException {

    private BusinessExceptionEnum businessExceptionEnum;

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

}
