package com.zhj.learn.aisuperhost.mq;


import lombok.Data;

import java.util.Date;

/**
 * 摘要生成事件
 */

@Data
public class SummaryGenerateEvent {

    private String eventId;
    private String sessionId;
    private Long evictedTurnNo;
    private Date triggerAt;


}
