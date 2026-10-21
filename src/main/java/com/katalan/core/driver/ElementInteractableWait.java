package com.katalan.core.driver;

import org.openqa.selenium.JavascriptException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.ScriptTimeoutException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Katalon's interactable wait before a click (WebUIAbstractKeyword.waitElementInteractable): the
 * visible, stable, enabled and topmost phases, run in that order as async scripts sharing one
 * timeout. The scripts are Katalon's own (ElementWaitingScript, WaitFor*Phase,
 * interactable-wait-util.js). A phase that times out or errors is logged as a warning and ends the
 * wait - the caller still clicks, exactly like Katalon.
 */
public final class ElementInteractableWait {

    private static final Logger logger = LoggerFactory.getLogger(ElementInteractableWait.class);

    private static final String ADD_DOM_OBSERVER_SCRIPT = String.join("\n",
            "async function waitWithDOMMutation(validator, timeout) {",
            "    return new Promise((resolve, reject) => {",
            "        try {",
            "            if (timeout <= 0) {",
            "                resolve(false);",
            "                return;",
            "            }",
            "            if (validator()) {",
            "                resolve(true);",
            "                return;",
            "            }",
            "            const observer = new MutationObserver((mutations) => {",
            "                if (validator()) {",
            "                    observer.disconnect();",
            "                    resolve(true);",
            "                }",
            "            });",
            "            observer.observe(document.body, { childList: true, attributes: true, subtree: true });",
            "            setTimeout(() => {",
            "                resolve(false);",
            "                observer.disconnect();",
            "            }, timeout);",
            "        } catch (error) {",
            "            reject(error);",
            "        }",
            "    });",
            "}",
            "");

    // ElementWaitingPhase.getWaitingScript(): shared preamble of every phase
    private static final String BASE_SCRIPT = ADD_DOM_OBSERVER_SCRIPT + String.join("\n",
            "const notifyWaitingResult = arguments[arguments.length - 1];",
            "const element = arguments[0];",
            "const timeout = arguments[1];",
            "const ElementWaitingInteractableState = {",
            "    DONE: 'DONE',",
            "    ELEMENT_DETACHED: 'ELEMENT_DETACHED',",
            "    TIMED_OUT: 'TIMED_OUT',",
            "    ERROR: 'ERROR',",
            "};",
            "function notifyWaitingError(error) {",
            "    notifyWaitingResult({ result: ElementWaitingInteractableState.ERROR, error: error.message });",
            "}",
            "waitWithDOMMutation(() => {",
            "    if (!element || !element.isConnected) {",
            "        return true;",
            "    }",
            "    return false;",
            "}, timeout).then((isDetached) => {",
            "    notifyWaitingResult({ result: isDetached",
            "        ? ElementWaitingInteractableState.ELEMENT_DETACHED",
            "        : ElementWaitingInteractableState.TIMED_OUT });",
            "}).catch(notifyWaitingError);",
            "");

    private static final String VISIBLE_PHASE = BASE_SCRIPT + String.join("\n",
            "function checkZeroSize(element) {",
            "    const elementRect = element?.getBoundingClientRect();",
            "    return elementRect?.width === 0 || elementRect?.height === 0;",
            "}",
            "function checkVisibility(element) {",
            "    if (checkZeroSize(element)) {",
            "        return false;",
            "    }",
            "    if (element.checkVisibility) {",
            "        return element.checkVisibility({",
            "            visibilityProperty: true,",
            "            checkVisibilityCSS: true",
            "        });",
            "    }",
            "    return element.style.display !== 'none' && element.style.visibility !== 'hidden';",
            "}",
            "waitWithDOMMutation(() => checkVisibility(element), timeout).then((waited) => {",
            "    notifyWaitingResult({ result: waited",
            "        ? ElementWaitingInteractableState.DONE",
            "        : ElementWaitingInteractableState.TIMED_OUT });",
            "}).catch(notifyWaitingError);");

    private static final String STABLE_PHASE = BASE_SCRIPT + String.join("\n",
            "function waitForAnimationEnd(element) {",
            "    return Promise.all(element.getAnimations({ subtree: true })",
            "        .filter(animation => {",
            "            const effect = animation.effect;",
            "          return window.getComputedStyle(effect.target, effect.pseudoElement)['animation-iteration-count'] !== 'infinite';",
            "        })",
            "        .map(animation => animation.finished));",
            "}",
            "element.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' });",
            "waitForAnimationEnd(element).then((waited) => {",
            "    notifyWaitingResult({ result: waited",
            "        ? ElementWaitingInteractableState.DONE",
            "        : ElementWaitingInteractableState.TIMED_OUT });",
            "}).catch(notifyWaitingError);");

    private static final String TOPMOST_PHASE = BASE_SCRIPT + String.join("\n",
            "const positions = [",
            "    { block: 'start', inline: 'start' },",
            "    { block: 'center', inline: 'center' },",
            "    { block: 'end', inline: 'end' },",
            "    { block: 'start', inline: 'center' },",
            "    { block: 'start', inline: 'end' },",
            "    { block: 'center', inline: 'start' },",
            "    { block: 'center', inline: 'end' },",
            "    { block: 'end', inline: 'start' },",
            "    { block: 'end', inline: 'center' }",
            "];",
            "function checkTopMost(element) {",
            "    var { left, top, width, height } = element.getBoundingClientRect();",
            "    var centerX = width / 2 + left;",
            "    var centerY = height / 2 + top;",
            "    var scopedDocument = element.getRootNode();",
            "    var elementAtPoint = scopedDocument.elementFromPoint(centerX, centerY);",
            "    var isTopMost = element === elementAtPoint || element.contains(elementAtPoint);",
            "    return isTopMost;",
            "}",
            "function checkAllTopMostPositions(element) {",
            "    if (checkTopMost(element)) {",
            "        return true;",
            "    }",
            "    return positions.some(position => {",
            "        element.scrollIntoView({",
            "            behavior: 'instant',",
            "            block: position.block,",
            "            inline: position.inline",
            "        });",
            "        return checkTopMost(element);",
            "    });",
            "}",
            "waitWithDOMMutation(() => checkAllTopMostPositions(element), timeout).then((waited) => {",
            "    notifyWaitingResult({ result: waited",
            "        ? ElementWaitingInteractableState.DONE",
            "        : ElementWaitingInteractableState.TIMED_OUT });",
            "}).catch(notifyWaitingError);");

    private static volatile String enabledPhase;

    private ElementInteractableWait() {}

    /**
     * @param startMillis when the keyword's attempt loop started; each phase gets what is left of
     *                    {@code timeoutMillis} (Katalon: {@code timeout - retryContext.getElapsedTime()})
     * @return true when every phase passed, false when one timed out or failed (already logged)
     */
    public static boolean waitInteractable(JavascriptExecutor executor, WebElement element,
                                           long timeoutMillis, long startMillis) {
        for (String phase : new String[] { VISIBLE_PHASE, STABLE_PHASE, enabledPhase(), TOPMOST_PHASE }) {
            long remaining = timeoutMillis - (System.currentTimeMillis() - startMillis);
            try {
                if (!runPhase(executor, phase, element, remaining)) {
                    return false;
                }
            } catch (IllegalArgumentException | IllegalStateException | ScriptTimeoutException e) {
                logger.warn(e.getMessage());
                return false;
            }
        }
        return true;
    }

    // ElementWaitingPhase.wait
    private static boolean runPhase(JavascriptExecutor executor, String script, WebElement element, long timeout) {
        try {
            Object result = executor.executeAsyncScript(script, element, timeout);
            if (result instanceof Map && ((Map<?, ?>) result).containsKey("result")) {
                Map<?, ?> map = (Map<?, ?>) result;
                switch (String.valueOf(map.get("result"))) {
                    case "DONE":
                        return true;
                    case "ELEMENT_DETACHED":
                        throw new IllegalStateException("Element is detached from the DOM while waiting interactable");
                    case "TIMED_OUT":
                        throw new IllegalStateException("Waiting for element timed out");
                    case "ERROR":
                        throw new IllegalStateException("Error during waiting interactable: " + map.get("error"));
                    default:
                        return false;
                }
            }
            return false;
        } catch (JavascriptException e) {
            throw new IllegalStateException("Error executing waiting script: " + e.getMessage(), e);
        }
    }

    private static String enabledPhase() {
        String script = enabledPhase;
        if (script == null) {
            script = loadUtilScript() + BASE_SCRIPT + String.join("\n",
                    "function checkEnabled(element) {",
                    "    return !getAriaDisabled(element);",
                    "}",
                    "waitWithDOMMutation(() => checkEnabled(element), timeout).then((waited) => {",
                    "    notifyWaitingResult({ result: waited",
                    "        ? ElementWaitingInteractableState.DONE",
                    "        : ElementWaitingInteractableState.TIMED_OUT });",
                    "}).catch(notifyWaitingError);");
            enabledPhase = script;
        }
        return script;
    }

    private static String loadUtilScript() {
        try (InputStream in = ElementInteractableWait.class.getResourceAsStream("/katalon/interactable-wait-util.js")) {
            if (in == null) {
                throw new JavascriptException("Failed to load interactable wait util script");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8) + "\n";
        } catch (IOException e) {
            throw new JavascriptException("Failed to load interactable wait util script", e);
        }
    }
}
