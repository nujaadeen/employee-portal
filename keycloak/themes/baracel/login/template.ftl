<#--
  Baracel page frame: brand panel on the left, card on the right (design/screens/01-login.png).
  Based on Keycloak's base template.ftl, keeping its scripts (session polling, one-click links).
-->
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}" lang="${lang}"<#if realm.internationalizationEnabled> dir="${(locale.rtl)?then('rtl','ltr')}"</#if>>
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>Baracel</title>
    <link rel="icon" type="image/png" href="${url.resourcesPath}/img/baracel-logo-dark.png" />
    <#list properties.styles?split(' ') as style>
        <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
    </#list>
    <script type="importmap">
        { "imports": { "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js" } }
    </script>
    <script src="${url.resourcesPath}/js/menu-button-links.js" type="module"></script>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="module">
        <#outputformat "JavaScript">
        import { startSessionPolling } from ${(url.resourcesPath + "/js/authChecker.js")?c};
        startSessionPolling(${url.ssoLoginInOtherTabsUrl?c});
        </#outputformat>
    </script>
    <script type="module">
        document.addEventListener("click", (event) => {
            const link = event.target.closest("a[data-once-link]");
            if (!link) { return; }
            if (link.getAttribute("aria-disabled") === "true") { event.preventDefault(); return; }
            link.setAttribute("role", "link");
            link.setAttribute("aria-disabled", "true");
        });
    </script>
    <#if authenticationSession??>
        <script type="module">
            <#outputformat "JavaScript">
            import { checkAuthSession } from ${(url.resourcesPath + "/js/authChecker.js")?c};
            checkAuthSession(${authenticationSession.authSessionIdHash?c});
            </#outputformat>
        </script>
    </#if>
</head>

<body class="${properties.kcBodyClass!}" data-page-id="login-${pageId}">
<div class="${properties.kcLoginClass!}">

    <section class="bc-brand" aria-label="Baracel">
        <div>
            <img class="bc-logo" src="${url.resourcesPath}/img/baracel-logo-transparent.png" alt="Baracel" />
        </div>
        <div class="bc-brand__body">
            <div class="bc-eyebrow">${msg("bcEyebrow")}</div>
            <p class="bc-headline">${msg("bcHeadline")}</p>
            <p class="bc-intro">${msg("bcIntro")}</p>
            <ul class="bc-points">
                <li><span class="bc-points__icon" aria-hidden="true"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg></span>${msg("bcBullet1")}</li>
                <li><span class="bc-points__icon" aria-hidden="true"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3v12"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/></svg></span>${msg("bcBullet2")}</li>
                <li><span class="bc-points__icon" aria-hidden="true"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M19 16v4M17 18h4"/></svg></span>${msg("bcBullet3")}</li>
            </ul>
        </div>
        <p class="bc-footnote">${msg("bcFootnote")}</p>
    </section>

    <main class="bc-main">
      <div class="bc-card">
        <#if pageId == "login" || pageId == "register">
            <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div class="bc-tabs">
                <a class="bc-tab" href="${url.loginUrl}" <#if pageId == "login">aria-current="page"</#if>>${msg("bcTabSignIn")}</a>
                <a class="bc-tab" href="${url.registrationUrl}" <#if pageId == "register">aria-current="page"</#if>>${msg("bcTabCreate")}</a>
            </div>
            </#if>
        </#if>

        <header class="${properties.kcFormHeaderClass!}">
            <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
                <h1 id="kc-page-title" class="bc-title"><#nested "header"></h1>
            <#else>
                <h1 id="kc-page-title" class="bc-title"><#nested "header"></h1>
                <#nested "show-username">
                <div id="kc-username" class="bc-attempted">
                    <span id="kc-attempted-username">${auth.attemptedUsername}</span>
                    <a id="reset-login" href="${url.loginRestartFlowUrl}" aria-label="${msg("restartLoginTooltip")}">${msg("restartLoginTooltip")}</a>
                </div>
            </#if>
            <#if pageId == "login"><p class="bc-sub">${msg("bcLoginSubtitle")}</p></#if>
            <#if pageId == "register"><p class="bc-sub">${msg("bcRegisterSubtitle")}</p></#if>
        </header>

        <#if displayRequiredFields>
            <p class="bc-required"><span aria-hidden="true">*</span> ${msg("requiredFields")}</p>
        </#if>

        <div id="kc-content">
          <div id="kc-content-wrapper">
            <#-- Keycloak errors, warnings, success and info messages, in the status colours. -->
            <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="bc-alert bc-alert--${message.type}" role="<#if message.type = 'error'>alert<#else>status</#if>">
                    <span class="bc-alert__icon" aria-hidden="true"><#if message.type = 'success'>✓<#elseif message.type = 'error'>!<#elseif message.type = 'warning'>!<#else>i</#if></span>
                    <span>${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink()>
                <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                    <input type="hidden" name="tryAnotherWay" value="on"/>
                    <a href="#" id="try-another-way" onclick="document.forms['kc-select-try-another-way-form'].requestSubmit();return false;">${msg("doTryAnotherWay")}</a>
                </form>
            </#if>

            <#nested "socialProviders">

            <#if displayInfo>
                <div id="kc-info" class="${properties.kcSignUpClass!}">
                    <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                        <#nested "info">
                    </div>
                </div>
            </#if>
          </div>
        </div>
      </div>
    </main>
</div>
</body>
</html>
</#macro>
