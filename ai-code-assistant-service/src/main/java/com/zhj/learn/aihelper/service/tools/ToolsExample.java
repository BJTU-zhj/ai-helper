package com.zhj.learn.aihelper.service.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
public class ToolsExample {

    @Tool("根据城市名称获取当天的天气情况")
    public  String getWeather(@P(value = "城市名称",required = true) String cityName){
        if (cityName.contains("北京")) return "晴朗，25度";
        if (cityName.contains("上海")) return "小雨，22度";
        return "晴到多云";
    }

}
