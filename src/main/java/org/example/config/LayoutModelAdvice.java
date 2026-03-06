package org.example.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 为所有使用 layout 的页面注入 contextPath，避免在模板中直接使用已禁用的 #request。
 */
@ControllerAdvice
public class LayoutModelAdvice {

    @ModelAttribute("contextPath")
    public String contextPath(HttpServletRequest request) {
        return request != null ? request.getContextPath() : "";
    }
}
