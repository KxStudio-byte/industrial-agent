package com.industrial.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码请求体。
 */
@Data
public class ChangePasswordRequest implements Serializable {

    @NotBlank(message = "原密码不能为空")
    @Size(min = 4, max = 64, message = "原密码长度需在 4~64 位之间")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度需在 6~32 位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码必须同时包含字母和数字")
    private String newPassword;
}