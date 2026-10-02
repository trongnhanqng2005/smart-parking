package vn.edu.huit.smartparking.backend.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountSecurityPageController {
    @GetMapping("/account/security")
    @PreAuthorize("hasRole('MANAGEMENT') and hasAuthority('SECURITY_CHANGE_OWN_PASSWORD')")
    public String security() {
        return "account/security";
    }
}
