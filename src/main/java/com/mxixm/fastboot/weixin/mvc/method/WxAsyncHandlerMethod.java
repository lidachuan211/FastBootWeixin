/*
 * Copyright (c) 2016-2017, Guangshan (guangshan1992@qq.com) and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mxixm.fastboot.weixin.mvc.method;

import com.mxixm.fastboot.weixin.exception.WxApiException;
import com.mxixm.fastboot.weixin.module.message.support.WxAsyncMessageTemplate;
import com.mxixm.fastboot.weixin.module.web.WxRequest;
import com.mxixm.fastboot.weixin.util.WxWebUtils;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * fastboot-weixin  WxAsyncHandlerMethod
 * 记录下拦截的过程，本来是不打算使用动态代理来实现的
 * 重写HandlerMethod类，在调用时获取被包装过的method和object，调用invoke直接返回，异步再调用真实method
 * 但是刚开始就出现了意料外的情况，因为Spring框架的ServletInvocableHandlerMethod(HandlerMethod handlerMethod)是这样构造出来的
 * 直接使用了handlerMethod里的原变量，所以我这里重写get是无效的，而且是final。无奈只能强制用反射设置了值。
 * 结果又出现了坑，因为我这个invoke接受Object...作为参数，我以为Method.invoke传入object[]，在这里是可以接收的。
 * 其实是不行的，method.invoke接收Object...之后，参数取出来为Object[]，而在真实调用时，又会把Object[]解析为一个一个的参数。
 * 而我的invoke方法接收Object...，其实是只有一个参数的，参数类型是数组。而上面invoke把Object[]解析为一个一个参数，而不是整体作为一个参数。于是就挂了
 *
 * @author Guangshan
 * @date 2017/10/18 22:40
 * @since 0.2.1
 */
public class WxAsyncHandlerMethod extends HandlerMethod {

    private final WxAsyncMethodHolder wxAsyncMethodHolder;

    public final static Method INVOKE_METHOD = ClassUtils.getMethod(WxAsyncMethodHolder.class, "invoke", (Class<?>[]) null);

    public WxAsyncHandlerMethod(Object bean, Method method, WxAsyncMessageTemplate wxAsyncMessageTemplate) {
        super(bean, method);
        this.wxAsyncMethodHolder = new WxAsyncMethodHolder(bean, method, wxAsyncMessageTemplate);
    }

    @Override
    protected Method getBridgedMethod() {
        return INVOKE_METHOD;
    }

    @Override
    public Object getBean() {
        return wxAsyncMethodHolder;
    }

    public WxAsyncHandlerMethod init() {

        Field field = null;
        try {
            field = HandlerMethod.class.getDeclaredField("bean");
            field.setAccessible(true);
            field.set(this, wxAsyncMethodHolder);
            field = HandlerMethod.class.getDeclaredField("bridgedMethod");
            field.setAccessible(true);
            field.set(this, INVOKE_METHOD);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;

    }

    public static class WxAsyncMethodHolder {

        private final Method method;

        private final Object bean;

        private final WxAsyncMessageTemplate wxAsyncMessageTemplate;

        public WxAsyncMethodHolder(Object bean, Method method, WxAsyncMessageTemplate wxAsyncMessageTemplate) {
            this.bean = bean;
            this.method = method;
            this.wxAsyncMessageTemplate = wxAsyncMessageTemplate;
        }

        public void invoke(Object... args) {
            WxRequest wxRequest = WxWebUtils.getWxRequestFromRequest();
            wxAsyncMessageTemplate.send(wxRequest, () -> {
                try {
                    return method.invoke(bean, args);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new WxApiException(e.getMessage(), e);
                }
            });
        }

    }


}
