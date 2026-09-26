package dev.bryrich.credapp.document;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.usergroup.CurrentUserGroupResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Upload, download and delete documents on a provider or a practice group. */
@Controller
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping("/providers/{id}/documents")
    public String uploadForProvider(@PathVariable long id, @AuthenticationPrincipal CredAppUserDetails principal,
                                    @RequestParam(required = false) MultipartFile file,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) String title,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expirationDate,
                                    RedirectAttributes redirect) {
        return upload(DocumentService.Owner.PROVIDER, id, principal, file, type, title, expirationDate, redirect);
    }

    @PostMapping("/groups/{id}/documents")
    public String uploadForGroup(@PathVariable long id, @AuthenticationPrincipal CredAppUserDetails principal,
                                 @RequestParam(required = false) MultipartFile file,
                                 @RequestParam(required = false) String type,
                                 @RequestParam(required = false) String title,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expirationDate,
                                 RedirectAttributes redirect) {
        return upload(DocumentService.Owner.GROUP, id, principal, file, type, title, expirationDate, redirect);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> download(@PathVariable long id, @AuthenticationPrincipal CredAppUserDetails principal,
                                           HttpServletRequest request) {
        DocumentService.Download file = documents.open(currentGroup(), id, principal.getUser().getId(),
                        principal.getUser().getEmail(), request.getRemoteAddr())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such document"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }

    @PostMapping("/documents/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        DocumentService.Location location = documents.delete(currentGroup(), id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such document"));
        redirect.addFlashAttribute("message", "Document deleted.");
        return "redirect:" + location.path();
    }

    private String upload(DocumentService.Owner owner, long id, CredAppUserDetails principal, MultipartFile file,
                          String type, String title, LocalDate expirationDate, RedirectAttributes redirect) {
        String back = new DocumentService.Location(owner, id).path();
        try {
            DocumentType documentType = type == null || type.isBlank() ? null : DocumentType.fromValue(type);
            byte[] content = file == null ? null : file.getBytes();
            documents.upload(currentGroup(), owner, id, documentType, title,
                    file == null ? null : file.getOriginalFilename(), content, expirationDate,
                    principal.getUser().getEmail());
            redirect.addFlashAttribute("message", "Document uploaded.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("documentError", ex.getMessage());
        } catch (IOException ex) {
            redirect.addFlashAttribute("documentError", "The upload didn't come through. Try again.");
        }
        return "redirect:" + back;
    }

    private static long currentGroup() {
        return new CurrentUserGroupResolver().resolveCurrentTenantIdentifier();
    }
}
