package com.ly.questionfind.questionfindserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class AnswerService {

    private static final long TASK_TTL_MS = TimeUnit.MINUTES.toMillis(10);

    private final DeepSeekClient deepSeekClient;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

    public AnswerService(
            DeepSeekClient deepSeekClient,
            @Value("${task.max-concurrent}") int maxConcurrent,
            @Value("${task.max-queue-size}") int maxQueueSize
    ) {
        this.deepSeekClient = deepSeekClient;
        this.executor = new ThreadPoolExecutor(
                maxConcurrent, maxConcurrent,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueueSize),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public String submit(String question) {
        cleanExpired();
        String taskId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();
        tasks.put(taskId, new Task(future, System.currentTimeMillis()));
        try {
            executor.execute(() -> {
                try {
                    future.complete(deepSeekClient.call(question));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            tasks.remove(taskId);
            throw new QueueFullException();
        }
        return taskId;
    }

    public AnswerResult get(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return AnswerResult.notFound();
        }
        if (!task.future().isDone()) {
            return AnswerResult.pending();
        }
        try {
            return AnswerResult.success(task.future().get());
        } catch (Exception e) {
            return AnswerResult.failed(e.getMessage());
        }
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        tasks.entrySet().removeIf(entry ->
                entry.getValue().future().isDone() &&
                        now - entry.getValue().createdAt() > TASK_TTL_MS
        );
    }

    public static final class QueueFullException extends RuntimeException {
    }

    public record AnswerResult(String status, String answer) {
        static AnswerResult pending() {
            return new AnswerResult("PENDING", null);
        }

        static AnswerResult notFound() {
            return new AnswerResult("NOT_FOUND", null);
        }

        static AnswerResult success(String answer) {
            return new AnswerResult("SUCCESS", answer);
        }

        static AnswerResult failed(String message) {
            return new AnswerResult("FAILED", message);
        }
    }

    private record Task(CompletableFuture<String> future, long createdAt) {
    }
}
