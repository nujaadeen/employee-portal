<#import "template.ftl" as layout>
<#macro socialButton p>
                        <li>
                            <a data-once-link id="social-${p.alias}" class="bc-btn bc-btn--secondary bc-btn--block bc-btn--large" href="${p.loginUrl}">
                                <#if p.alias == "google">
                                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.2c0-.7-.1-1.3-.2-1.9H12v3.6h5.1a4.4 4.4 0 0 1-1.9 2.9v2.4h3.1c1.8-1.7 2.7-4.1 2.7-7z"/><path d="M12 21c2.6 0 4.7-.9 6.3-2.3l-3.1-2.4c-.9.6-2 .9-3.2.9-2.5 0-4.5-1.7-5.3-3.9H3.5v2.5A9.5 9.5 0 0 0 12 21z"/><path d="M6.7 13.3a5.7 5.7 0 0 1 0-3.6V7.2H3.5a9.5 9.5 0 0 0 0 8.6z"/><path d="M12 6.8c1.4 0 2.7.5 3.7 1.4l2.7-2.7A9.5 9.5 0 0 0 3.5 7.2l3.2 2.5C7.5 8.5 9.5 6.8 12 6.8z"/></svg>
                                <#elseif p.alias == "github">
                                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4"/><path d="M9 18c-4.51 2-5-2-7-2"/></svg>
                                <#elseif p.alias == "facebook">
                                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z"/></svg>
                                </#if>
                                <span>${msg("bcContinueWith", p.displayName!)}</span>
                            </a>
                        </li>
</#macro>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=false; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <#-- Social buttons come first, built from the realm's identity providers (google, github, facebook). -->
        <#if realm.password && social?? && social.providers?has_content>
            <div id="kc-social-providers" class="bc-social">
                <ul class="bc-social-list">
                    <#-- Google, GitHub, Facebook first (the design order), any other provider after them. -->
                    <#assign order = ["google", "github", "facebook"]>
                    <#list order as a><#list social.providers as p><#if p.alias == a><@socialButton p /></#if></#list></#list>
                    <#list social.providers as p><#if !order?seq_contains(p.alias)><@socialButton p /></#if></#list>
                </ul>
                <div class="bc-divider"><span>${msg("bcOrEmail")}</span></div>
            </div>
        </#if>

        <#if realm.password>
            <form id="kc-form-login" class="bc-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                <#if !usernameHidden??>
                    <div class="bc-field">
                        <label for="username" class="bc-label"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                        <input tabindex="1" id="username" class="bc-input" name="username" value="${(login.username!'')}" type="text"
                               autofocus autocomplete="username" dir="ltr"
                               aria-invalid="<#if messagesPerField.existsError('username','password')>true<#else>false</#if>" />
                        <#if messagesPerField.existsError('username','password')>
                            <span id="input-error" class="bc-error" role="alert">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
                        </#if>
                    </div>
                </#if>

                <div class="bc-field">
                    <label for="password" class="bc-label">${msg("password")}</label>
                    <div class="bc-input-group" dir="ltr">
                        <input tabindex="2" id="password" class="bc-input" name="password" type="password" autocomplete="current-password"
                               aria-invalid="<#if messagesPerField.existsError('username','password')>true<#else>false</#if>" />
                        <button class="bc-eye" type="button" aria-label="${msg("showPassword")}" aria-controls="password" data-password-toggle tabindex="-1"
                                data-icon-show="bc-eye-show" data-icon-hide="bc-eye-hide" data-label-show="${msg('showPassword')}" data-label-hide="${msg('hidePassword')}">
                            <i class="bc-eye-show" aria-hidden="true"></i>
                        </button>
                    </div>
                    <#if usernameHidden?? && messagesPerField.existsError('username','password')>
                        <span id="input-error" class="bc-error" role="alert">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
                    </#if>
                </div>

                <div class="bc-options">
                    <#if realm.rememberMe && !usernameHidden??>
                        <label class="bc-checkbox" for="rememberMe">
                            <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if !(login.rememberMe)?? || login.rememberMe == "true">checked</#if>>
                            <span>${msg("rememberMe")}</span>
                        </label>
                    <#else>
                        <span></span>
                    </#if>
                    <#if realm.resetPasswordAllowed>
                        <a tabindex="5" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                    </#if>
                </div>

                <div id="kc-form-buttons">
                    <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                    <input tabindex="4" class="bc-btn bc-btn--primary bc-btn--block" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                </div>
            </form>
            <p class="bc-note"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg><span>${msg("bcApprovalNote")}</span></p>
        </#if>
        <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
    </#if>
</@layout.registrationLayout>
