const kAncestorPreventingLandmark = 'article:not([role]), aside:not([role]), main:not([role]), nav:not([role]), section:not([role]), [role=article], [role=complementary], [role=main], [role=navigation], [role=region]';
function hasExplicitAccessibleName(e) {
  return e.hasAttribute("aria-label") || e.hasAttribute("aria-labelledby");
}
function enclosingShadowHost(element) {
  while (element.parentElement)
    element = element.parentElement;
  return parentElementOrShadowHost(element);
}
function parentElementOrShadowHost(element) {
  if (element.parentElement)
    return element.parentElement;
  if (!element.parentNode)
    return;
  if (element.parentNode.nodeType === 11 && element.parentNode.host)
    return element.parentNode.host;
}
function enclosingShadowRootOrDocument(element) {
  let node = element;
  while (node.parentNode)
    node = node.parentNode;
  if (node.nodeType === 11 || node.nodeType === 9)
    return node;
}
function getIdRefs(element, ref) {
  if (!ref)
    return [];
  const root = enclosingShadowRootOrDocument(element);
  if (!root)
    return [];
  try {
    const ids = ref.split(" ").filter((id) => !!id);
    const set = /* @__PURE__ */ new Set();
    for (const id of ids) {
      const firstElement = root.querySelector("#" + CSS.escape(id));
      if (firstElement)
        set.add(firstElement);
    }
    return [...set];
  } catch (e) {
    return [];
  }
}
var kAriaDisabledRoles = ["application", "button", "composite", "gridcell", "group", "input", "link", "menuitem", "scrollbar", "separator", "tab", "checkbox", "columnheader", "combobox", "grid", "listbox", "menu", "menubar", "menuitemcheckbox", "menuitemradio", "option", "radio", "radiogroup", "row", "rowheader", "searchbox", "select", "slider", "spinbutton", "switch", "tablist", "textbox", "toolbar", "tree", "treegrid", "treeitem"];
var kImplicitRoleByTagName = {
  "A": (e) => {
    return e.hasAttribute("href") ? "link" : null;
  },
  "AREA": (e) => {
    return e.hasAttribute("href") ? "link" : null;
  },
  "ARTICLE": () => "article",
  "ASIDE": () => "complementary",
  "BLOCKQUOTE": () => "blockquote",
  "BUTTON": () => "button",
  "CAPTION": () => "caption",
  "CODE": () => "code",
  "DATALIST": () => "listbox",
  "DD": () => "definition",
  "DEL": () => "deletion",
  "DETAILS": () => "group",
  "DFN": () => "term",
  "DIALOG": () => "dialog",
  "DT": () => "term",
  "EM": () => "emphasis",
  "FIELDSET": () => "group",
  "FIGURE": () => "figure",
  "FOOTER": (e) => closestCrossShadow(e, kAncestorPreventingLandmark) ? null : "contentinfo",
  "FORM": (e) => hasExplicitAccessibleName(e) ? "form" : null,
  "H1": () => "heading",
  "H2": () => "heading",
  "H3": () => "heading",
  "H4": () => "heading",
  "H5": () => "heading",
  "H6": () => "heading",
  "HEADER": (e) => closestCrossShadow(e, kAncestorPreventingLandmark) ? null : "banner",
  "HR": () => "separator",
  "HTML": () => "document",
  "IMG": (e) => e.getAttribute("alt") === "" && !hasGlobalAriaAttribute(e) && Number.isNaN(Number(String(e.getAttribute("tabindex")))) ? "presentation" : "img",
  "INPUT": (e) => {
    const type = e.type.toLowerCase();
    if (type === "search")
      return e.hasAttribute("list") ? "combobox" : "searchbox";
    if (["email", "tel", "text", "url", ""].includes(type)) {
      const list = getIdRefs(e, e.getAttribute("list"))[0];
      return list && list.tagName === "DATALIST" ? "combobox" : "textbox";
    }
    if (type === "hidden")
      return "";
    return {
      "button": "button",
      "checkbox": "checkbox",
      "image": "button",
      "number": "spinbutton",
      "radio": "radio",
      "range": "slider",
      "reset": "button",
      "submit": "button"
    }[type] || "textbox";
  },
  "INS": () => "insertion",
  "LI": () => "listitem",
  "MAIN": () => "main",
  "MARK": () => "mark",
  "MATH": () => "math",
  "MENU": () => "list",
  "METER": () => "meter",
  "NAV": () => "navigation",
  "OL": () => "list",
  "OPTGROUP": () => "group",
  "OPTION": () => "option",
  "OUTPUT": () => "status",
  "P": () => "paragraph",
  "PROGRESS": () => "progressbar",
  "SECTION": (e) => hasExplicitAccessibleName(e) ? "region" : null,
  "SELECT": (e) => e.hasAttribute("multiple") || e.size > 1 ? "listbox" : "combobox",
  "STRONG": () => "strong",
  "SUB": () => "subscript",
  "SUP": () => "superscript",
  // For <svg> we default to Chrome behavior:
  // - Chrome reports 'img'.
  // - Firefox reports 'diagram' that is not in official ARIA spec yet.
  // - Safari reports 'no role', but still computes accessible name.
  "SVG": () => "img",
  "TABLE": () => "table",
  "TBODY": () => "rowgroup",
  "TD": (e) => {
    const table = closestCrossShadow(e, "table");
    const role = table ? getExplicitAriaRole(table) : "";
    return role === "grid" || role === "treegrid" ? "gridcell" : "cell";
  },
  "TEXTAREA": () => "textbox",
  "TFOOT": () => "rowgroup",
  "TH": (e) => {
    if (e.getAttribute("scope") === "col")
      return "columnheader";
    if (e.getAttribute("scope") === "row")
      return "rowheader";
    const table = closestCrossShadow(e, "table");
    const role = table ? getExplicitAriaRole(table) : "";
    return role === "grid" || role === "treegrid" ? "gridcell" : "cell";
  },
  "THEAD": () => "rowgroup",
  "TIME": () => "time",
  "TR": () => "row",
  "UL": () => "list"
};
var kPresentationInheritanceParents = {
  "DD": ["DL", "DIV"],
  "DIV": ["DL"],
  "DT": ["DL", "DIV"],
  "LI": ["OL", "UL"],
  "TBODY": ["TABLE"],
  "TD": ["TR"],
  "TFOOT": ["TABLE"],
  "TH": ["TR"],
  "THEAD": ["TABLE"],
  "TR": ["THEAD", "TBODY", "TFOOT", "TABLE"]
};
function getImplicitAriaRole(element) {
  var _a;
  const implicitRole = ((_a = kImplicitRoleByTagName[element.tagName.toUpperCase()]) == null ? void 0 : _a.call(kImplicitRoleByTagName, element)) || "";
  if (!implicitRole)
    return null;
  let ancestor = element;
  while (ancestor) {
    const parent = parentElementOrShadowHost(ancestor);
    const parents = kPresentationInheritanceParents[ancestor.tagName];
    if (!parents || !parent || !parents.includes(parent.tagName))
      break;
    const parentExplicitRole = getExplicitAriaRole(parent);
    if ((parentExplicitRole === "none" || parentExplicitRole === "presentation") && !hasPresentationConflictResolution(parent))
      return parentExplicitRole;
    ancestor = parent;
  }
  return implicitRole;
}
var allRoles = [
  "alert",
  "alertdialog",
  "application",
  "article",
  "banner",
  "blockquote",
  "button",
  "caption",
  "cell",
  "checkbox",
  "code",
  "columnheader",
  "combobox",
  "command",
  "complementary",
  "composite",
  "contentinfo",
  "definition",
  "deletion",
  "dialog",
  "directory",
  "document",
  "emphasis",
  "feed",
  "figure",
  "form",
  "generic",
  "grid",
  "gridcell",
  "group",
  "heading",
  "img",
  "input",
  "insertion",
  "landmark",
  "link",
  "list",
  "listbox",
  "listitem",
  "log",
  "main",
  "marquee",
  "math",
  "meter",
  "menu",
  "menubar",
  "menuitem",
  "menuitemcheckbox",
  "menuitemradio",
  "navigation",
  "none",
  "note",
  "option",
  "paragraph",
  "presentation",
  "progressbar",
  "radio",
  "radiogroup",
  "range",
  "region",
  "roletype",
  "row",
  "rowgroup",
  "rowheader",
  "scrollbar",
  "search",
  "searchbox",
  "section",
  "sectionhead",
  "select",
  "separator",
  "slider",
  "spinbutton",
  "status",
  "strong",
  "structure",
  "subscript",
  "superscript",
  "switch",
  "tab",
  "table",
  "tablist",
  "tabpanel",
  "term",
  "textbox",
  "time",
  "timer",
  "toolbar",
  "tooltip",
  "tree",
  "treegrid",
  "treeitem",
  "widget",
  "window"
];
var abstractRoles = ["command", "composite", "input", "landmark", "range", "roletype", "section", "sectionhead", "select", "structure", "widget", "window"];
var validRoles = allRoles.filter((role) => !abstractRoles.includes(role));
function getExplicitAriaRole(element) {
  const roles = (element.getAttribute("role") || "").split(" ").map((role) => role.trim());
  return roles.find((role) => validRoles.includes(role)) || null;
}
const kGlobalAriaAttributes = [
  ['aria-atomic', undefined],
  ['aria-busy', undefined],
  ['aria-controls', undefined],
  ['aria-current', undefined],
  ['aria-describedby', undefined],
  ['aria-details', undefined],
  // Global use deprecated in ARIA 1.2
  // ['aria-disabled', undefined],
  ['aria-dropeffect', undefined],
  // Global use deprecated in ARIA 1.2
  // ['aria-errormessage', undefined],
  ['aria-flowto', undefined],
  ['aria-grabbed', undefined],
  // Global use deprecated in ARIA 1.2
  // ['aria-haspopup', undefined],
  ['aria-hidden', undefined],
  // Global use deprecated in ARIA 1.2
  // ['aria-invalid', undefined],
  ['aria-keyshortcuts', undefined],
  ['aria-label', ['caption', 'code', 'deletion', 'emphasis', 'generic', 'insertion', 'paragraph', 'presentation', 'strong', 'subscript', 'superscript']],
  ['aria-labelledby', ['caption', 'code', 'deletion', 'emphasis', 'generic', 'insertion', 'paragraph', 'presentation', 'strong', 'subscript', 'superscript']],
  ['aria-live', undefined],
  ['aria-owns', undefined],
  ['aria-relevant', undefined],
  ['aria-roledescription', ['generic']],
];
function hasGlobalAriaAttribute(element, forRole) {
  return kGlobalAriaAttributes.some(([attr, prohibited]) => {
    return !prohibited?.includes(forRole || '') && element.hasAttribute(attr);
  });
}
function isNativelyFocusable(element) {
  const tagName = elementSafeTagName(element);
  if (['BUTTON', 'DETAILS', 'SELECT', 'TEXTAREA'].includes(tagName))
    return true;
  if (tagName === 'A' || tagName === 'AREA')
    return element.hasAttribute('href');
  if (tagName === 'INPUT')
    return !element?.hidden;
  return false;
}
function belongsToDisabledOptGroup(element) {
  return elementSafeTagName(element) === 'OPTION' && !!element.closest('OPTGROUP[DISABLED]');
}
function isNativelyDisabled(element) {
  const isNativeFormControl = ['BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'OPTION', 'OPTGROUP'].includes(elementSafeTagName(element));
  return isNativeFormControl && (element.hasAttribute('disabled') || belongsToDisabledOptGroup(element) || belongsToDisabledFieldSet(element));
}
function hasTabIndex(element) {
  return !Number.isNaN(Number(String(element.getAttribute('tabindex'))));
}
function isFocusable(element) {
  return !isNativelyDisabled(element) && (isNativelyFocusable(element) || hasTabIndex(element));
}
function hasPresentationConflictResolution(element, role) {
  // https://www.w3.org/TR/wai-aria-1.2/#conflict_resolution_presentation_none
  return hasGlobalAriaAttribute(element, role) || isFocusable(element);
}
function getAriaRole(element) {
  const explicitRole = getExplicitAriaRole(element);
  if (!explicitRole)
    return getImplicitAriaRole(element);
  if ((explicitRole === "none" || explicitRole === "presentation") && hasPresentationConflictResolution(element))
    return getImplicitAriaRole(element);
  return explicitRole;
}
function belongsToDisabledFieldSet(element) {
  if (!element)
    return false;
  if (element.tagName === "FIELDSET" && element.hasAttribute("disabled"))
    return true;
  return belongsToDisabledFieldSet(element.parentElement);
}
function hasExplicitAriaDisabled(element) {
  if (!element)
    return false;
  if (kAriaDisabledRoles.includes(getAriaRole(element) || "")) {
    const attribute = (element.getAttribute("aria-disabled") || "").toLowerCase();
    if (attribute === "true")
      return true;
    if (attribute === "false")
      return false;
  }
  return hasExplicitAriaDisabled(parentElementOrShadowHost(element));
}
function getAriaDisabled(element) {
  const isNativeFormControl = ["BUTTON", "INPUT", "SELECT", "TEXTAREA", "OPTION", "OPTGROUP"].includes(element.tagName);
  if (isNativeFormControl && (element.hasAttribute("disabled") || belongsToDisabledFieldSet(element)))
    return true;
  return hasExplicitAriaDisabled(element);
}
// Assumption: if scope is provided, element must be inside scope's subtree.
function closestCrossShadow(element, css, scope) {
  while (element) {
    const closest = element.closest(css);
    if (scope && closest !== scope && closest?.contains(scope))
      return;
    if (closest)
      return closest;
    element = enclosingShadowHost(element);
  }
}
function elementSafeTagName(element) {
  // Named inputs, e.g. <input name=tagName>, will be exposed as fields on the parent <form>
  // and override its properties.
  if (element instanceof HTMLFormElement)
    return 'FORM';
  // Elements from the svg namespace do not have uppercase tagName right away.
  return element.tagName.toUpperCase();
}
// https://w3c.github.io/aria/#aria-readonly
const kAriaReadonlyRoles = ['checkbox', 'combobox', 'grid', 'gridcell', 'listbox', 'radiogroup', 'slider', 'spinbutton', 'textbox', 'columnheader', 'rowheader', 'searchbox', 'switch', 'treegrid'];
function getReadonly(element) {
  const tagName = elementSafeTagName(element);
  // https://www.w3.org/TR/wai-aria-1.2/#aria-checked
  // https://www.w3.org/TR/html-aam-1.0/#html-attribute-state-and-property-mappings
  if (['INPUT', 'TEXTAREA', 'SELECT'].includes(tagName))
    return element.hasAttribute('readonly');
  if (kAriaReadonlyRoles.includes(getAriaRole(element) || ''))
    return element.getAttribute('aria-readonly') === 'true';
  if ((element).isContentEditable)
    return false;
  return 'error';
}