package com.zhj.learn.aicommon.util;

import cn.hutool.core.util.IdUtil;

/**
 * 雪花算法
 */

public class SnowUtil {

    private static long workerId;
    private static long datacenterId;

    public static  long getSnowflakeId() {
        return IdUtil.getSnowflake(workerId, datacenterId).nextId();
    }

    public static String getSnowflakeIdStr() {
        return IdUtil.getSnowflake(workerId, datacenterId).nextIdStr();
    }
}
