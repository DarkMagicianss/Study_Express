package com.example.express.controller;

import com.example.express.common.constant.SecurityConstant;
import com.example.express.domain.bean.SysUser;
import com.example.express.domain.enums.ResponseErrorCodeEnum;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
public class PageController extends BaseController {
    /**
     * 登陆成功页
     */
    @RequestMapping(SecurityConstant.LOGIN_SUCCESS_URL)
    public void showSuccessPage(@AuthenticationPrincipal SysUser sysUser, HttpServletResponse response) throws IOException {
        String homePage = getHomePage(sysUser.getRole());
        response.sendRedirect("/" + homePage);
    }

    /**
     * 处理验证码错误
     */
    @RequestMapping(SecurityConstant.VALIDATE_CODE_ERR_URL)
    public String codeError(ModelMap map) {
        map.put("message", ResponseErrorCodeEnum.VERIFY_CODE_ERROR.getMsg());
        return "login";
    }

    /**
     * 跳转到登录页
     */
    @RequestMapping(SecurityConstant.UN_AUTHENTICATION_URL)
    public String showAuthenticationPage(HttpServletRequest request, HttpServletResponse response, ModelMap map) {
        AuthenticationException exception =
                (AuthenticationException)request.getSession()
                        .getAttribute("SPRING_SECURITY_LAST_EXCEPTION");
        if(exception != null) {
            if(exception instanceof BadCredentialsException) {
                map.put("message", ResponseErrorCodeEnum.PASSWORD_ERROR.getMsg());
            } else {
                map.put("message", exception.getMessage());
            }
        }

        request.getSession().removeAttribute("SPRING_SECURITY_LAST_EXCEPTION");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return "login";
    }

    /**
     * 跳转到信息补全页面
     * @author jitwxs
     * @date 2019/4/21 22:14
     */
    @RequestMapping("/completeInfo")
    public String showCompleteInfoPage() {
        return "completeInfo";
    }

    @GetMapping("/register")
    public String showRegister() { return "register"; }
}