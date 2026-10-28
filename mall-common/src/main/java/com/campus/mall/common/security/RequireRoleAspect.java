package com.campus.mall.common.security;

import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * {@link RequireRole} 的落地：先要求登录，再比对角色。
 */
@Aspect
public class RequireRoleAspect {

    @Around("@annotation(com.campus.mall.common.security.RequireRole) "
            + "|| @within(com.campus.mall.common.security.RequireRole)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        RequireRole anno = method.getAnnotation(RequireRole.class);
        if (anno == null) {
            anno = pjp.getTarget().getClass().getAnnotation(RequireRole.class);
        }
        if (anno != null) {
            String role = UserContext.role();
            if (role == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            String[] allowed = anno.value();
            if (allowed.length > 0) {
                boolean isSuper = MallConstants.ROLE_SUPER_ADMIN.equals(role);
                if (!isSuper && Arrays.stream(allowed).noneMatch(a -> a.equals(role))) {
                    throw new BizException(ErrorCode.FORBIDDEN);
                }
            }
        }
        return pjp.proceed();
    }
}
