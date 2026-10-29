package com.ly.questionfind.questionfindserver;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/answer")
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping
    public ResponseEntity<SubmitResponse> submit(@RequestBody Map<String, String> body) {
        String question = body == null ? null : body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String taskId = answerService.submit(question);
            return ResponseEntity.accepted().body(new SubmitResponse(taskId));
        } catch (AnswerService.QueueFullException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ResultResponse> get(@PathVariable String taskId) {
        AnswerService.AnswerResult result = answerService.get(taskId);
        if ("NOT_FOUND".equals(result.status())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ResultResponse(result.status(), result.answer()));
    }

    record SubmitResponse(String taskId) {
    }

    record ResultResponse(String status, String answer) {
    }
}
