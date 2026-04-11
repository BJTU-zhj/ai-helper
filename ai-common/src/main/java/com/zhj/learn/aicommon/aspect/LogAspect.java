package com.zhj.learn.aicommon.aspect;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Aspect
@Component
public class LogAspect {

    LogAspect() {
        System.out.println("Common Aspect");
    }

    private static final Logger LOG = LoggerFactory.getLogger(LogAspect.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /*
    定义切点
     */

    @Pointcut("execution(public * com.zhj..*Controller.*(..))")
    public void controllerPointCut() {
    }

    @Before("controllerPointCut()")
    public void doBefore(JoinPoint joinPoint) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        Signature signature = joinPoint.getSignature();
        String name = signature.getName();

        //打印请求信息
        LOG.info("----------------------开始执行---------------------");
        LOG.info("请求地址：{} {}", request.getRequestURL().toString(), request.getMethod());
        LOG.info("类名方法：{} {}", signature.getDeclaringTypeName(), name);
        LOG.info("远程地址：{}", request.getRemoteAddr());

        //打印请求参数
        Object[] args = joinPoint.getArgs();

        //排除特殊类型的参数
        Object[] arguments = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ServletRequest || args[i] instanceof ServletResponse || args[i] instanceof MultipartFile) {
                continue;
            }
            arguments[i] = args[i];
        }

        LOG.info("请求参数: {}", toJson(arguments));
    }

    @Around("controllerPointCut()")
    public Object doAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = proceedingJoinPoint.proceed();
        LOG.info("返回结果：{}", toJson(result));
        LOG.info("耗时：{} ms", System.currentTimeMillis() - startTime);
        return result;
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
