package com.zhj.learn.aihelper.service.rag;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class RagIngestionTaskService {

    @Resource
    @Qualifier("ragJdbcTemplate")
    private JdbcTemplate ragJdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createTask(String taskId, OffsetDateTime startedAt) {
        ragJdbcTemplate.update("""
                INSERT INTO kb_ingest_task (task_id, task_name, status, total_docs, success_docs, fail_docs, started_at, finished_at)
                VALUES (?, ?, 'RUNNING', 0, 0, 0, ?, null)
                """, taskId, "DOCX_INGEST", startedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateTaskProgress(String taskId, String status, int total, int success, int fail, String error, OffsetDateTime finishedAt) {
        ragJdbcTemplate.update("""
                UPDATE kb_ingest_task
                SET status = ?,
                    total_docs = ?,
                    success_docs = ?,
                    fail_docs = ?,
                    error_message = ?,
                    finished_at = ?
                WHERE task_id = ?
                """, status, total, success, fail, error, finishedAt, taskId);
    }
}

