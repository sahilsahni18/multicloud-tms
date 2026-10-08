package com.trackflow.tms.controller;

import com.trackflow.tms.dto.ticket.CommentRequest;
import com.trackflow.tms.dto.ticket.CommentResponse;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Discussion on tickets")
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/tickets/{ticketId}/comments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Comments on a ticket, oldest first")
    public List<CommentResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long ticketId) {
        return commentService.list(user, ticketId);
    }

    @PostMapping("/tickets/{ticketId}/comments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Comment on a ticket I can see")
    public ResponseEntity<CommentResponse> add(@AuthenticationPrincipal AuthUser user, @PathVariable Long ticketId,
                                               @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.add(user, ticketId, request));
    }

    @PutMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Edit my comment (admins: any comment)")
    public CommentResponse edit(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                @Valid @RequestBody CommentRequest request) {
        return commentService.edit(user, id, request);
    }

    @DeleteMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete my comment (admins: any comment)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        commentService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
