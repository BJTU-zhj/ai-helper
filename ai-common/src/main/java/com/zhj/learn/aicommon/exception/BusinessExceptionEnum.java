package com.zhj.learn.aicommon.exception;

public enum BusinessExceptionEnum {

    MEMBER_MOBILE_EXIST("错误错误！！！！");



    private String desc;

    BusinessExceptionEnum(String desc){
        this.desc = desc;
    }

    public String getDesc(){
        return desc;
    }

    public static String getDescByCode(String code){
        for(BusinessExceptionEnum item : BusinessExceptionEnum.values()){
            if(item.name().equalsIgnoreCase(code)){
                return item.getDesc();
            }
        }
        return "";
    }

}
