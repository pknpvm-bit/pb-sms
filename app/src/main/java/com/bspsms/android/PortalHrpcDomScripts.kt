package com.bspsms.android

import org.json.JSONObject

object PortalHrpcDomScripts {

    private fun sharedRowActionJs(): String = """
        function _bspNormalizeText(s) {
            return String(s || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
        }
        function _bspGetColMap(tbl) {
            var headers = Array.from(tbl.querySelectorAll('thead tr th, thead tr td, tr:first-child th, tr:first-child td'));
            var map = { roll: -1, name: -1, view: -1, print: -1, action: -1, headers: headers.map(function(c) { return _bspNormalizeText(c.innerText).toLowerCase(); }) };
            map.headers.forEach(function(txt, idx) {
                if (/roll/.test(txt) && map.roll === -1) map.roll = idx;
                else if ((/student\s*name|\bname\b/.test(txt)) && map.name === -1) map.name = idx;
                else if (/\bview\b/.test(txt) && !/print/.test(txt) && map.view === -1) map.view = idx;
                else if (/\bprint\b/.test(txt) && !/view/.test(txt) && map.print === -1) map.print = idx;
                else if (/action|marksheet|report|view.*print|print.*view/.test(txt) && map.action === -1) map.action = idx;
            });
            return map;
        }
        function _bspElementOwnAndChildMeta(el) {
            var cls = typeof el.className === 'string' ? el.className : (el.getAttribute('class') || '');
            var parts = [
                el.innerText || '',
                el.textContent || '',
                el.value || '',
                el.title || '',
                el.id || '',
                el.name || '',
                cls,
                el.getAttribute('aria-label') || '',
                el.getAttribute('data-original-title') || '',
                el.getAttribute('data-bs-original-title') || '',
                el.getAttribute('data-title') || '',
                el.getAttribute('data-tooltip') || '',
                el.getAttribute('data-action') || '',
                el.getAttribute('href') || '',
                el.getAttribute('onclick') || ''
            ];
            var nodes = el.querySelectorAll('i, span, svg, img, path, use');
            for (var i = 0; i < nodes.length; i++) {
                var n = nodes[i];
                var nCls = typeof n.className === 'string' ? n.className : (n.getAttribute('class') || '');
                parts.push(nCls);
                parts.push(n.id || '');
                parts.push(n.alt || '');
                parts.push(n.title || '');
                parts.push(n.getAttribute('src') || '');
                parts.push(n.getAttribute('href') || '');
                parts.push(n.getAttribute('xlink:href') || '');
                parts.push(n.getAttribute('data-original-title') || '');
                parts.push(n.getAttribute('data-bs-original-title') || '');
                parts.push(n.getAttribute('aria-label') || '');
            }
            var nonHtmlMeta = parts.join(' ').toLowerCase();
            var htmlMeta = (el.innerHTML || '').toLowerCase();
            return { nonHtml: nonHtmlMeta, full: nonHtmlMeta + ' ' + htmlMeta };
        }
        function _bspCollectRowClickables(container) {
            if (!container) return [];
            var raw = Array.from(container.querySelectorAll('a, button, input[type="button"], input[type="submit"], [role="button"], span[onclick], i[onclick], img[onclick], td i.fa, td i[class*="icon"], td svg'));
            var filtered = raw.filter(function(el) {
                var parentCtrl = el.parentElement ? el.parentElement.closest('a, button, input[type="button"], input[type="submit"], [role="button"]') : null;
                if (parentCtrl && parentCtrl !== el && container.contains(parentCtrl)) return false;
                var st = null;
                try { st = window.getComputedStyle(el); } catch(e) {}
                if (st && (st.display === 'none' || st.visibility === 'hidden')) return false;
                if (el.tagName === 'I' || el.tagName === 'SVG') {
                    var clickableStyle = st && st.cursor === 'pointer';
                    if (!el.getAttribute('onclick') && !clickableStyle && !el.closest('td')) return false;
                }
                return true;
            });
            return filtered.length > 0 ? filtered : Array.from(container.querySelectorAll('a, button, input[type="button"], input[type="submit"], [role="button"], [onclick]'));
        }
        function _bspFindActionControl(tbl, row, actionWord, map) {
            if (!map) map = _bspGetColMap(tbl);
            var cells = Array.from(row.querySelectorAll('td'));
            var childRow = (row.nextElementSibling && row.nextElementSibling.classList && row.nextElementSibling.classList.contains('child')) ? row.nextElementSibling : null;

            function matchesPrint(el, tdIdx) {
                var m = _bspElementOwnAndChildMeta(el);
                if (/(?:^|[^a-z])(print|printer|pdf)(?:[^a-z]|${'$'})|fa-print|glyphicon-print|bi-printer|mdi-printer|la-print|print_hrpc|printcard|printmarksheet|printreport/i.test(m.nonHtml)) return true;
                if (/fa-print|glyphicon-print|bi-printer|mdi-printer|printer|print_icon|print-icon/i.test(m.full)) return true;
                if (tdIdx >= 0 && map.headers[tdIdx] && /print/.test(map.headers[tdIdx]) && !/view/.test(map.headers[tdIdx])) return true;
                return false;
            }
            function matchesView(el, tdIdx) {
                var m = _bspElementOwnAndChildMeta(el);
                if (/(?:^|[^a-z])(view|preview|eye|marksheet)(?:[^a-z]|${'$'})|fa-eye|glyphicon-eye|bi-eye|mdi-eye|view_hrpc|viewcard|viewmarksheet/i.test(m.nonHtml)) return true;
                if (/fa-eye|glyphicon-eye|bi-eye|mdi-eye/i.test(m.full)) return true;
                if (tdIdx >= 0 && map.headers[tdIdx] && /view/.test(map.headers[tdIdx]) && !/print/.test(map.headers[tdIdx])) return true;
                return false;
            }

            var allCtrls = _bspCollectRowClickables(row);
            if (childRow) {
                allCtrls = allCtrls.concat(_bspCollectRowClickables(childRow));
            }
            if (allCtrls.length === 0) return null;

            // 1. Direct explicit column match
            if (actionWord === 'print' && map.print >= 0 && cells[map.print]) {
                var pColCtrls = _bspCollectRowClickables(cells[map.print]);
                if (pColCtrls.length > 0) return pColCtrls[0];
            }
            if (actionWord === 'view' && map.view >= 0 && cells[map.view]) {
                var vColCtrls = _bspCollectRowClickables(cells[map.view]);
                if (vColCtrls.length > 0) return vColCtrls[0];
            }

            // 2. Attribute / class / icon / SVG / title / onclick / href match
            for (var i = 0; i < allCtrls.length; i++) {
                var ctrl = allCtrls[i];
                var td = ctrl.closest('td');
                var tdIdx = td ? cells.indexOf(td) : -1;
                if (actionWord === 'print' && matchesPrint(ctrl, tdIdx)) return ctrl;
                if (actionWord === 'view' && matchesView(ctrl, tdIdx)) return ctrl;
            }

            // 3. Actions cell & positional pair fallback (View = 1st control, Print = 2nd control)
            var actionCell = (map.action >= 0 && cells[map.action]) ? cells[map.action] : null;
            var actionCtrls = actionCell ? _bspCollectRowClickables(actionCell) : [];
            if (actionCtrls.length === 0) {
                actionCtrls = allCtrls.filter(function(el) {
                    var td = el.closest('td');
                    var idx = td ? cells.indexOf(td) : -1;
                    if (idx === -1) return true;
                    if (idx === map.roll || idx === map.name) return false;
                    return true;
                });
                for (var c = cells.length - 1; c >= 0; c--) {
                    var cCtrls = _bspCollectRowClickables(cells[c]);
                    if (cCtrls.length >= 2) {
                        actionCtrls = cCtrls;
                        break;
                    }
                }
            }

            var viewCtrl = null;
            for (var j = 0; j < actionCtrls.length; j++) {
                var vtd = actionCtrls[j].closest('td');
                if (matchesView(actionCtrls[j], vtd ? cells.indexOf(vtd) : -1)) {
                    viewCtrl = actionCtrls[j];
                    break;
                }
            }

            if (actionWord === 'view') {
                if (viewCtrl) return viewCtrl;
                if (actionCtrls.length >= 1) return actionCtrls[0];
                return allCtrls[0];
            }

            if (actionWord === 'print') {
                if (viewCtrl) {
                    var vPos = actionCtrls.indexOf(viewCtrl);
                    if (vPos >= 0 && vPos + 1 < actionCtrls.length) {
                        return actionCtrls[vPos + 1];
                    }
                    var other = actionCtrls.find(function(c) { return c !== viewCtrl; });
                    if (other) return other;
                }
                if (actionCtrls.length >= 2) {
                    return actionCtrls[1];
                }
                if (allCtrls.length >= 2 && map.roll !== 0) {
                    return allCtrls[1];
                }
            }
            return null;
        }
    """.trimIndent()

    private fun sameSessionInterceptorCoreJs(): String = """
        if (!window.__bspSameSessionHooked) {
            window.__bspSameSessionHooked = true;
            window.__bspOrigWindowOpen = window.open;
            window.__bspOrigFormSubmit = HTMLFormElement.prototype.submit;
            window.__bspOrigFormRequestSubmit = HTMLFormElement.prototype.requestSubmit;
            window.__bspBgCaptureActive = false;
            window.__bspAutomationMainTabLock = false;
            window.__bspCapturedNav = null;

            function _bspAbsUrl(u) {
                if (!u) return '';
                var s = String(u).trim();
                if (!s || s === '#' || /^#/i.test(s) || /^javascript:/i.test(s) || /^about:blank/i.test(s)) return '';
                try {
                    return new URL(s, window.location.href).href;
                } catch (e) {
                    return s;
                }
            }

            function _bspSerializeFormSafe(formEl, submitBtn) {
                if (!formEl) return null;
                var rawAction = (formEl.getAttribute ? (formEl.getAttribute('action') || '') : '').trim();
                if (/^javascript:/i.test(rawAction)) return null;
                var actionUrl = _bspAbsUrl(rawAction || window.location.href);
                if (!actionUrl) return null;
                var method = ((formEl.getAttribute ? (formEl.getAttribute('method') || 'GET') : 'GET').trim() || 'GET').toUpperCase();
                var params = [];
                var fields = formEl.querySelectorAll ? formEl.querySelectorAll('input, select, textarea') : [];
                for (var i = 0; i < fields.length; i++) {
                    var el = fields[i];
                    if (!el.name || el.disabled) continue;
                    var type = (el.type || '').toLowerCase();
                    if ((type === 'checkbox' || type === 'radio') && !el.checked) continue;
                    if (type === 'submit' || type === 'button' || type === 'reset' || type === 'image' || type === 'file') continue;
                    if (el.tagName === 'SELECT' && el.multiple) {
                        Array.from(el.options).forEach(function(opt) {
                            if (opt.selected) params.push({ name: el.name, value: opt.value });
                        });
                    } else {
                        params.push({ name: el.name, value: el.value || '' });
                    }
                }
                if (submitBtn && submitBtn.name) {
                    params.push({ name: submitBtn.name, value: submitBtn.value || '' });
                }
                return { mode: 'form', url: actionUrl, method: method, params: params, source: 'form_lock' };
            }

            function _bspPublishCapturedNav(navObj) {
                if (!navObj) return;
                window.__bspCapturedNav = navObj;
                if (!window.__bspBgCaptureActive && window.__bspAutomationMainTabLock &&
                    window.BspAndroidBridge && window.BspAndroidBridge.dispatchCapturedBackgroundTarget) {
                    try {
                        window.BspAndroidBridge.dispatchCapturedBackgroundTarget(JSON.stringify(navObj));
                    } catch (e) {}
                }
            }

            HTMLFormElement.prototype.submit = function() {
                if (window.__bspBgCaptureActive || window.__bspAutomationMainTabLock) {
                    var ser = _bspSerializeFormSafe(this, null);
                    if (ser && ser.url) {
                        _bspPublishCapturedNav(ser);
                        return;
                    }
                    return;
                }
                return window.__bspOrigFormSubmit.apply(this, arguments);
            };

            if (typeof window.__bspOrigFormRequestSubmit === 'function') {
                HTMLFormElement.prototype.requestSubmit = function(submitter) {
                    if (window.__bspBgCaptureActive || window.__bspAutomationMainTabLock) {
                        var ser = _bspSerializeFormSafe(this, submitter || null);
                        if (ser && ser.url) {
                            _bspPublishCapturedNav(ser);
                            return;
                        }
                        return;
                    }
                    return window.__bspOrigFormRequestSubmit.apply(this, arguments);
                };
            }

            document.addEventListener('click', function(ev) {
                if (!window.__bspBgCaptureActive && !window.__bspAutomationMainTabLock) return;
                var anchor = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
                if (anchor) {
                    var abs = _bspAbsUrl(anchor.getAttribute('href'));
                    if (abs) {
                        ev.preventDefault();
                        _bspPublishCapturedNav({ mode: 'url', url: abs, source: 'click_lock' });
                    }
                }
            }, true);

            document.addEventListener('submit', function(ev) {
                if (!window.__bspBgCaptureActive && !window.__bspAutomationMainTabLock) return;
                var ser = _bspSerializeFormSafe(ev.target, ev.submitter || null);
                ev.preventDefault();
                if (ser && ser.url) {
                    _bspPublishCapturedNav(ser);
                }
            }, true);

            window.open = function(url, name, specs) {
                var u = (url === undefined || url === null) ? '' : String(url).trim();
                var abs = (u && !/^about:blank/i.test(u) && !/^javascript:/i.test(u)) ? _bspAbsUrl(u) : '';
                if (window.__bspBgCaptureActive || window.__bspAutomationMainTabLock) {
                    if (abs) {
                        _bspPublishCapturedNav({ mode: 'url', url: abs, source: 'window_open' });
                    }
                    var capturedUrl = abs;
                    var dummyDocHtml = '';
                    return {
                        closed: false,
                        focus: function() {},
                        blur: function() {},
                        close: function() {},
                        print: function() { window.__bspPrintTriggered = true; },
                        document: {
                            open: function() {},
                            close: function() {
                                if (dummyDocHtml) {
                                    _bspPublishCapturedNav({ mode: 'html', html: dummyDocHtml, baseUrl: window.location.href, source: 'window_open_doc' });
                                }
                            },
                            write: function(s) {
                                dummyDocHtml += String(s || '');
                                _bspPublishCapturedNav({ mode: 'html', html: dummyDocHtml, baseUrl: window.location.href, source: 'window_open_doc' });
                            },
                            writeln: function(s) {
                                dummyDocHtml += String(s || '') + '\n';
                                _bspPublishCapturedNav({ mode: 'html', html: dummyDocHtml, baseUrl: window.location.href, source: 'window_open_doc' });
                            }
                        },
                        location: {
                            get href() { return capturedUrl || window.location.href; },
                            set href(v) {
                                var nextAbs = _bspAbsUrl(v);
                                if (nextAbs) {
                                    capturedUrl = nextAbs;
                                    _bspPublishCapturedNav({ mode: 'url', url: nextAbs, source: 'window_open_loc' });
                                }
                            },
                            assign: function(v) {
                                var nextAbs = _bspAbsUrl(v);
                                if (nextAbs) {
                                    capturedUrl = nextAbs;
                                    _bspPublishCapturedNav({ mode: 'url', url: nextAbs, source: 'window_open_assign' });
                                }
                            },
                            replace: function(v) {
                                var nextAbs = _bspAbsUrl(v);
                                if (nextAbs) {
                                    capturedUrl = nextAbs;
                                    _bspPublishCapturedNav({ mode: 'url', url: nextAbs, source: 'window_open_replace' });
                                }
                            }
                        }
                    };
                }
                return window.__bspOrigWindowOpen ? window.__bspOrigWindowOpen.apply(window, arguments) : null;
            };
        }
    """.trimIndent()

    fun setAutomationMainTabLockJs(locked: Boolean): String = """
        (function() {
            ${sameSessionInterceptorCoreJs()}
            window.__bspAutomationMainTabLock = ${if (locked) "true" else "false"};
            if (window.__bspAutomationMainTabLock) {
                window.__bspCapturedNav = null;
            }
            return 'ok';
        })();
    """.trimIndent()

    fun installSameSessionPopupInterceptorJs(): String = """
        (function() {
            ${sameSessionInterceptorCoreJs()}
            return 'ok';
        })();
    """.trimIndent()

    fun snapshotHrpcStateJs(): String = """
        (function() {
            function findSelect(patterns) {
                var selects = Array.from(document.querySelectorAll('select'));
                for (var p = 0; p < patterns.length; p++) {
                    var rx = patterns[p];
                    for (var i = 0; i < selects.length; i++) {
                        var s = selects[i];
                        var lbl = '';
                        if (s.id) {
                            var l = document.querySelector('label[for="' + s.id + '"]');
                            if (l) lbl = l.innerText || '';
                        }
                        if (!lbl && s.closest('.form-group, td, div')) {
                            lbl = (s.closest('.form-group, td, div').innerText || '').split('\n')[0] || '';
                        }
                        var meta = ((s.name || '') + ' ' + (s.id || '') + ' ' + lbl).toLowerCase();
                        if (rx.test(meta)) {
                            var selText = s.options && s.selectedIndex >= 0 ? (s.options[s.selectedIndex].text || '').trim() : '';
                            return { value: s.value || '', text: selText };
                        }
                    }
                }
                return { value: '', text: '' };
            }

            var pageInfo = { startEntry: 0, endEntry: 0, totalEntries: 0, currentPage: 1, totalPages: 1 };
            var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
            var infoEl = document.querySelector('.dataTables_info, [id*="_info"]');
            var infoTxt = infoEl ? infoEl.innerText : bodyTxt;
            var m = infoTxt.match(/Showing\s+(\d+)\s+to\s+(\d+)\s+of\s+(\d+)\s+entries/i);
            if (m) {
                pageInfo.startEntry = parseInt(m[1], 10) || 0;
                pageInfo.endEntry = parseInt(m[2], 10) || 0;
                pageInfo.totalEntries = parseInt(m[3], 10) || 0;
            }
            var activePageEl = document.querySelector('.dataTables_paginate .paginate_button.active, .pagination li.active a, .pagination li.active span, .paginate_button.current');
            if (activePageEl) {
                var pn = parseInt((activePageEl.innerText || '').trim(), 10);
                if (pn > 0) pageInfo.currentPage = pn;
            }
            var pageNums = Array.from(document.querySelectorAll('.dataTables_paginate .paginate_button, .pagination li a, .pagination li span'))
                .map(function(el) { return parseInt((el.innerText || '').trim(), 10); })
                .filter(function(n) { return !isNaN(n) && n > 0; });
            if (pageNums.length > 0) {
                pageInfo.totalPages = Math.max.apply(null, pageNums);
            } else if (pageInfo.totalEntries > 0) {
                var perPage = (pageInfo.endEntry - pageInfo.startEntry + 1);
                if (perPage > 0) pageInfo.totalPages = Math.max(1, Math.ceil(pageInfo.totalEntries / perPage));
            }

            var hrpcMenuHref = '';
            var navLinks = Array.from(document.querySelectorAll('a[href]'));
            for (var n = 0; n < navLinks.length; n++) {
                var a = navLinks[n];
                var h = (a.href || '').trim();
                var t = (a.innerText || a.title || '').toLowerCase();
                if (!h || /^javascript:/i.test(h) || h.indexOf('#') === h.length - 1) continue;
                if (/hrpc|holistic.*progress|progress.*report.*card|marksheet/i.test(h + ' ' + t) &&
                    !/print|pdf|download/i.test(h)) {
                    hrpcMenuHref = h;
                    break;
                }
            }

            return JSON.stringify({
                url: location.href,
                referrer: document.referrer || '',
                hrpcMenuHref: hrpcMenuHref,
                academicYear: findSelect([/academic|session|year/]),
                medium: findSelect([/medium/]),
                className: findSelect([/\bclass\b|grade/]),
                section: findSelect([/section/]),
                currentPage: pageInfo.currentPage,
                totalPages: pageInfo.totalPages,
                totalEntries: pageInfo.totalEntries,
                startEntry: pageInfo.startEntry,
                endEntry: pageInfo.endEntry
            });
        })();
    """.trimIndent()

    fun openHrpcMenuFromDashboardJs(): String = """
        (function() {
            var links = Array.from(document.querySelectorAll('a[href]'));
            var candidates = links.filter(function(a) {
                var h = (a.href || '').trim();
                var t = (a.innerText || a.title || '').toLowerCase();
                if (!h || /^javascript:/i.test(h) || h.indexOf('#') === h.length - 1) return false;
                return (/hrpc|holistic|progress.*report|marksheet/i.test(h + ' ' + t) && !/print|pdf|download/i.test(h));
            });
            if (candidates.length > 0) {
                candidates[0].setAttribute('target', '_self');
                candidates[0].click();
                return JSON.stringify({ clicked: true, href: candidates[0].href });
            }
            return JSON.stringify({ clicked: false });
        })();
    """.trimIndent()

    fun scanHrpcTableJs(): String = """
        (function() {
            var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
            var hasAuthForm = !!document.querySelector('input[type="password"], input[name*="captcha" i], img[src*="captcha" i]');
            if (hasAuthForm) {
                return JSON.stringify({ ok: false, sessionExpired: true, reason: 'Portal sign-in page is displayed. Please sign in first.' });
            }
            if (/An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered/i.test(bodyTxt)) {
                return JSON.stringify({
                    ok: false,
                    sessionExpired: false,
                    dbError: true,
                    url: location.href,
                    reason: 'Portal database error on current page. Recovering cleanly...'
                });
            }

            ${sharedRowActionJs()}

            var tables = Array.from(document.querySelectorAll('table'));
            var bestTable = null;
            var bestColMap = null;
            var bestScore = -1;

            for (var t = 0; t < tables.length; t++) {
                var tbl = tables[t];
                var headers = Array.from(tbl.querySelectorAll('thead tr th, thead tr td, tr:first-child th, tr:first-child td'));
                if (headers.length === 0) continue;
                var map = _bspGetColMap(tbl);

                var dataRows = Array.from(tbl.querySelectorAll('tbody tr'));
                if (dataRows.length === 0) {
                    dataRows = Array.from(tbl.querySelectorAll('tr')).filter(function(r) { return !r.querySelector('th'); });
                }

                var hasViewOrPrintButtons = false;
                for (var r = 0; r < Math.min(dataRows.length, 5); r++) {
                    var rowTxt = (dataRows[r].innerText || '').toLowerCase();
                    if (/\bview\b|\bprint\b/i.test(rowTxt) ||
                        _bspCollectRowClickables(dataRows[r]).length > 0) {
                        hasViewOrPrintButtons = true;
                        break;
                    }
                }

                var score = 0;
                if (map.roll >= 0) score += 3;
                if (map.name >= 0) score += 3;
                if (map.view >= 0 || map.print >= 0 || map.action >= 0) score += 2;
                if (hasViewOrPrintButtons) score += 3;
                if (dataRows.length > 0) score += 1;

                if (score > bestScore && dataRows.length > 0 && (map.roll >= 0 || map.name >= 0 || hasViewOrPrintButtons)) {
                    bestScore = score;
                    bestTable = tbl;
                    bestColMap = map;
                }
            }

            if (!bestTable) {
                return JSON.stringify({
                    ok: false,
                    sessionExpired: false,
                    dbError: false,
                    reason: 'Student Information table not found. Select Academic Year, Medium, Class, Section and click Search first.'
                });
            }

            bestTable.setAttribute('data-bsp-student-table', 'true');

            var isTableProcessing = false;
            var procEls = document.querySelectorAll('.dataTables_processing');
            for (var p = 0; p < procEls.length; p++) {
                try {
                    var pst = window.getComputedStyle(procEls[p]);
                    var prect = procEls[p].getBoundingClientRect();
                    if (pst.display !== 'none' && pst.visibility !== 'hidden' && parseFloat(pst.opacity || '1') > 0.05 && prect.width > 2 && prect.height > 2) {
                        isTableProcessing = true;
                        break;
                    }
                } catch (e) {}
            }

            var rows = Array.from(bestTable.querySelectorAll('tbody tr'));
            if (rows.length === 0) {
                rows = Array.from(bestTable.querySelectorAll('tr')).filter(function(r) { return !r.querySelector('th'); });
            }

            var students = [];
            rows.forEach(function(row, idx) {
                if (row.classList && row.classList.contains('child')) return;
                var cells = Array.from(row.querySelectorAll('td'));
                if (cells.length === 0) return;
                var rowFullTxt = (row.innerText || '').trim();
                if (/no\s*(?:data|matching\s*records)\s*(?:available|found)/i.test(rowFullTxt)) return;
                if (/^(?:loading|processing)\.{0,3}$/i.test(rowFullTxt)) {
                    isTableProcessing = true;
                    return;
                }

                var rollIdx = bestColMap.roll >= 0 ? bestColMap.roll : 0;
                var nameIdx = bestColMap.name >= 0 ? bestColMap.name : (cells.length > 1 ? 1 : 0);

                var rollNo = cells[rollIdx] ? (cells[rollIdx].innerText || '').trim() : '';
                var name = cells[nameIdx] ? (cells[nameIdx].innerText || '').trim() : '';

                if (!name && rollNo) {
                    name = 'STUDENT_' + rollNo;
                }
                if (!rollNo && !name) return;

                var viewEl = _bspFindActionControl(bestTable, row, 'view', bestColMap);
                var printEl = _bspFindActionControl(bestTable, row, 'print', bestColMap);

                row.setAttribute('data-bsp-row-idx', String(idx));
                students.push({
                    rowIndex: idx,
                    rollNo: rollNo.replace(/\s+/g, ' ').trim(),
                    name: name.replace(/\s+/g, ' ').trim(),
                    hasView: !!viewEl,
                    hasPrint: !!printEl,
                    viewHref: viewEl && viewEl.tagName === 'A' ? (viewEl.getAttribute('href') || '') : '',
                    printHref: printEl && printEl.tagName === 'A' ? (printEl.getAttribute('href') || '') : ''
                });
            });

            var infoEl = document.querySelector('.dataTables_info, [id*="_info"]');
            var infoTxt = infoEl ? infoEl.innerText : bodyTxt;
            var startEntry = 0, endEntry = 0, totalEntries = students.length, currentPage = 1, totalPages = 1;
            var m = infoTxt.match(/Showing\s+(\d+)\s+to\s+(\d+)\s+of\s+(\d+)\s+entries/i);
            if (m) {
                startEntry = parseInt(m[1], 10) || 0;
                endEntry = parseInt(m[2], 10) || 0;
                totalEntries = parseInt(m[3], 10) || students.length;
            }
            var activePageEl = document.querySelector('.dataTables_paginate .paginate_button.active, .pagination li.active a, .pagination li.active span, .paginate_button.current');
            if (activePageEl) {
                var pn = parseInt((activePageEl.innerText || '').trim(), 10);
                if (pn > 0) currentPage = pn;
            }
            var pageNums = Array.from(document.querySelectorAll('.dataTables_paginate .paginate_button, .pagination li a, .pagination li span'))
                .map(function(el) { return parseInt((el.innerText || '').trim(), 10); })
                .filter(function(n) { return !isNaN(n) && n > 0; });
            if (pageNums.length > 0) {
                totalPages = Math.max.apply(null, pageNums);
            } else if (totalEntries > 0 && endEntry >= startEntry && startEntry > 0) {
                var perPage = endEntry - startEntry + 1;
                totalPages = Math.max(1, Math.ceil(totalEntries / perPage));
            }

            var nextBtn = document.querySelector('.dataTables_paginate .paginate_button.next:not(.disabled), li.next:not(.disabled) a, a.next:not(.disabled), button.next:not([disabled])');
            var hasNext = false;
            if (nextBtn) {
                var cls = (nextBtn.className || '') + ' ' + ((nextBtn.parentElement && nextBtn.parentElement.className) || '');
                hasNext = !/disabled/i.test(cls);
            }
            if (!hasNext && currentPage < totalPages) hasNext = true;

            return JSON.stringify({
                ok: true,
                sessionExpired: false,
                dbError: false,
                isProcessing: isTableProcessing,
                url: location.href,
                currentPage: currentPage,
                totalPages: totalPages,
                startEntry: startEntry,
                endEntry: endEntry,
                totalEntries: totalEntries,
                hasNextPage: hasNext,
                students: students
            });
        })();
    """.trimIndent()

    fun triggerStudentActionJs(
        rowIndex: Int,
        rollNo: String,
        studentName: String,
        actionType: String
    ): String {
        val qRoll = JSONObject.quote(rollNo)
        val qName = JSONObject.quote(studentName)
        val qAction = JSONObject.quote(actionType)
        return """
            (function() {
                ${sameSessionInterceptorCoreJs()}
                window.__bspPrintTriggered = false;
                if (!window.__bspOrigPrint) {
                    window.__bspOrigPrint = window.print;
                }
                window.print = function() {
                    window.__bspPrintTriggered = true;
                };

                ${sharedRowActionJs()}

                function _bspResolveUrl(u) {
                    if (!u) return '';
                    var s = String(u).trim();
                    if (!s || s === '#' || /^#/i.test(s) || /^javascript:/i.test(s) || /^about:blank/i.test(s)) return '';
                    try { return new URL(s, window.location.href).href; } catch (e) { return s; }
                }

                function _bspSerializeForm(formEl, submitBtn) {
                    if (!formEl) return null;
                    var rawAction = (formEl.getAttribute('action') || '').trim();
                    if (/^javascript:/i.test(rawAction)) return null;
                    var actionUrl = _bspResolveUrl(rawAction || window.location.href);
                    var method = ((formEl.getAttribute('method') || 'GET').trim() || 'GET').toUpperCase();
                    var params = [];
                    var fields = formEl.querySelectorAll('input, select, textarea');
                    for (var i = 0; i < fields.length; i++) {
                        var el = fields[i];
                        if (!el.name || el.disabled) continue;
                        var type = (el.type || '').toLowerCase();
                        if ((type === 'checkbox' || type === 'radio') && !el.checked) continue;
                        if (type === 'submit' || type === 'button' || type === 'reset' || type === 'image' || type === 'file') continue;
                        if (el.tagName === 'SELECT' && el.multiple) {
                            Array.from(el.options).forEach(function(opt) {
                                if (opt.selected) params.push({ name: el.name, value: opt.value });
                            });
                        } else {
                            params.push({ name: el.name, value: el.value || '' });
                        }
                    }
                    if (submitBtn && submitBtn.name) {
                        params.push({ name: submitBtn.name, value: submitBtn.value || '' });
                    }
                    return { mode: 'form', url: actionUrl, method: method, params: params, source: 'form_inspect' };
                }

                function _bspExtractStaticTarget(ctrl) {
                    if (!ctrl) return null;
                    var onclickRaw = (ctrl.getAttribute('onclick') || '').trim();
                    if (ctrl.tagName === 'A') {
                        var rawHref = (ctrl.getAttribute('href') || '').trim();
                        var absHref = _bspResolveUrl(rawHref);
                        if (absHref && (!onclickRaw || /window\.open\s*\(|location\./i.test(onclickRaw))) {
                            return { mode: 'url', url: absHref, source: 'anchor_href' };
                        }
                    }
                    var dataAttrs = ['data-url', 'data-href', 'data-link', 'data-print-url', 'data-view-url', 'data-target-url', 'data-src'];
                    for (var d = 0; d < dataAttrs.length; d++) {
                        var dv = _bspResolveUrl(ctrl.getAttribute(dataAttrs[d]));
                        if (dv) return { mode: 'url', url: dv, source: dataAttrs[d] };
                    }
                    if (onclickRaw) {
                        var mOpen = onclickRaw.match(/(?:window\.open|location\.assign|location\.replace)\s*\(\s*['"]([^'"]+)['"]/i) ||
                            onclickRaw.match(/location(?:\.href)?\s*=\s*['"]([^'"]+)['"]/i) ||
                            onclickRaw.match(/['"](https?:\/\/[^'"]+)['"]/i) ||
                            onclickRaw.match(/['"]([^'"]*\/(?:print|view|marksheet|report|hrpc)[^'"]*)['"]/i);
                        if (mOpen && mOpen[1]) {
                            var resolvedOnclick = _bspResolveUrl(mOpen[1]);
                            if (resolvedOnclick) {
                                return { mode: 'url', url: resolvedOnclick, source: 'onclick_url' };
                            }
                        }
                    }
                    var formEl = ctrl.closest ? ctrl.closest('form') : null;
                    if (formEl) {
                        var isSubmit = (ctrl.tagName === 'BUTTON' && (!ctrl.type || ctrl.type.toLowerCase() === 'submit')) ||
                            (ctrl.tagName === 'INPUT' && ctrl.type && ctrl.type.toLowerCase() === 'submit') ||
                            !!formEl.getAttribute('action');
                        if (isSubmit) {
                            var serialized = _bspSerializeForm(formEl, ctrl);
                            if (serialized && serialized.url) return serialized;
                        }
                    }
                    return null;
                }

                var tbl = document.querySelector('table[data-bsp-student-table="true"]') ||
                    Array.from(document.querySelectorAll('table')).find(function(t) {
                        return t.querySelector('tbody tr');
                    });
                if (!tbl) return JSON.stringify({ ok: false, error: 'Student table missing on page' });

                var rows = Array.from(tbl.querySelectorAll('tbody tr')).filter(function(r) {
                    return !(r.classList && r.classList.contains('child'));
                });
                if (rows.length === 0) rows = Array.from(tbl.querySelectorAll('tr')).filter(function(r) { return !r.querySelector('th'); });

                var targetRow = tbl.querySelector('tr[data-bsp-row-idx="' + ${rowIndex} + '"]');
                var wantRoll = ($qRoll || '').replace(/\s+/g, ' ').trim().toLowerCase();
                var wantName = ($qName || '').replace(/\s+/g, ' ').trim().toLowerCase();
                if (!targetRow || (wantName && _bspNormalizeText(targetRow.innerText).toLowerCase().indexOf(wantName) === -1)) {
                    targetRow = rows.find(function(r) {
                        var txt = _bspNormalizeText(r.innerText).toLowerCase();
                        return (wantName && txt.indexOf(wantName) !== -1) && (!wantRoll || txt.indexOf(wantRoll) !== -1);
                    }) || rows.find(function(r) {
                        var txt = _bspNormalizeText(r.innerText).toLowerCase();
                        return wantRoll && txt.indexOf(wantRoll) !== -1;
                    }) || rows[${rowIndex}];
                }
                if (!targetRow) return JSON.stringify({ ok: false, error: 'Student row not found: ' + $qName });

                var colMap = _bspGetColMap(tbl);
                var primaryAction = ($qAction || 'print').toLowerCase();
                var secondaryAction = (primaryAction === 'print') ? 'view' : 'print';

                var chosen = _bspFindActionControl(tbl, targetRow, primaryAction, colMap);
                var usedAction = primaryAction;
                var altCtrl = _bspFindActionControl(tbl, targetRow, secondaryAction, colMap);
                if (!chosen && altCtrl) {
                    chosen = altCtrl;
                    usedAction = secondaryAction;
                    altCtrl = null;
                }

                if (!chosen) {
                    return JSON.stringify({ ok: false, error: 'Action button "' + primaryAction + '" not found for ' + $qName });
                }

                var fallbackTarget = altCtrl ? _bspExtractStaticTarget(altCtrl) : null;
                var staticTarget = _bspExtractStaticTarget(chosen);
                if (staticTarget) {
                    return JSON.stringify({
                        ok: true,
                        mode: staticTarget.mode,
                        url: staticTarget.url || '',
                        method: staticTarget.method || 'GET',
                        params: staticTarget.params || [],
                        source: staticTarget.source || '',
                        usedAction: usedAction,
                        fallback: fallbackTarget
                    });
                }

                // Dynamic JS handler fallback: keep main tab locked and intercept window.open / form.submit / anchor navigation WITHOUT navigating portalWebView
                window.__bspAutomationMainTabLock = true;
                window.__bspBgCaptureActive = true;
                window.__bspCapturedNav = null;

                try {
                    if (typeof chosen.click === 'function') {
                        chosen.click();
                    } else {
                        chosen.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                    }
                } finally {
                    window.__bspBgCaptureActive = false;
                }

                if (window.__bspCapturedNav) {
                    var cap = window.__bspCapturedNav;
                    return JSON.stringify({
                        ok: true,
                        mode: cap.mode || 'url',
                        url: cap.url || '',
                        method: cap.method || 'GET',
                        params: cap.params || [],
                        html: cap.html || '',
                        baseUrl: cap.baseUrl || '',
                        source: cap.source || 'sync_capture',
                        usedAction: usedAction,
                        fallback: fallbackTarget
                    });
                }

                // Check if click opened an in-page Bootstrap dialog / modal
                var modalEl = document.querySelector('.modal.show, .modal.in, [role="dialog"]:not([hidden])');
                if (modalEl) {
                    var fr = modalEl.querySelector('iframe[src]');
                    var frSrc = fr ? _bspResolveUrl(fr.getAttribute('src')) : '';
                    if (frSrc) {
                        return JSON.stringify({
                            ok: true,
                            mode: 'url',
                            url: frSrc,
                            source: 'modal_iframe',
                            closeModalFirst: true,
                            usedAction: usedAction,
                            fallback: fallbackTarget
                        });
                    }
                    var modalBody = modalEl.querySelector('.modal-body') || modalEl;
                    var modalText = (modalBody.innerText || '');
                    if (/Summative|Progress\s+Report|Marksheet|Roll/i.test(modalText)) {
                        var headHtml = Array.from(document.querySelectorAll('head link[rel="stylesheet"], head style'))
                            .map(function(n) { return n.outerHTML; }).join('\n');
                        var docHtml = '<!DOCTYPE html><html><head><meta charset="utf-8">' + headHtml + '</head><body>' + modalBody.innerHTML + '</body></html>';
                        return JSON.stringify({
                            ok: true,
                            mode: 'html',
                            html: docHtml,
                            baseUrl: window.location.href,
                            closeModalFirst: true,
                            source: 'modal_html_clone',
                            usedAction: usedAction,
                            fallback: fallbackTarget
                        });
                    }
                }

                // Async handler (e.g. jQuery AJAX -> window.open or form.submit or redirect caught while __bspAutomationMainTabLock is active)
                return JSON.stringify({
                    ok: true,
                    mode: 'async_pending',
                    source: 'async_hook',
                    usedAction: usedAction,
                    fallback: fallbackTarget
                });
            })();
        """.trimIndent()
    }

    fun suppressAutoPrintJs(): String = """
        (function() {
            if (!window.__bspOrigPrint) {
                window.__bspOrigPrint = window.print;
            }
            window.print = function() {
                window.__bspPrintTriggered = true;
            };
            return 'ok';
        })();
    """.trimIndent()

    fun checkMarksheetRenderJs(): String = """
        (function() {
            ${sameSessionInterceptorCoreJs()}
            if (!window.__bspOrigPrint) {
                window.__bspOrigPrint = window.print;
            }
            window.print = function() {
                window.__bspPrintTriggered = true;
            };

            function collectBodyTextAndDoc() {
                var txt = (document.body && document.body.innerText) ? document.body.innerText : '';
                var docToCheck = document;
                var frames = Array.from(document.querySelectorAll('iframe'));
                for (var f = 0; f < frames.length; f++) {
                    try {
                        var fDoc = frames[f].contentDocument;
                        if (fDoc && fDoc.body && (fDoc.body.innerText || '').length > 100) {
                            txt += '\n' + fDoc.body.innerText;
                            if (/Summative\s+Evaluation/i.test(fDoc.body.innerText)) {
                                docToCheck = fDoc;
                            }
                        }
                    } catch (e) {}
                }
                return { bodyTxt: txt, doc: docToCheck };
            }

            var ctx = collectBodyTextAndDoc();
            var bodyTxt = ctx.bodyTxt;
            var doc = ctx.doc;

            var hasAuthForm = !!doc.querySelector('input[type="password"], input[name*="captcha" i], img[src*="captcha" i]');
            var hasPortalError = /An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered|404\s+Page\s+Not\s+Found/i.test(bodyTxt);

            var hasStudentListTable = !!doc.querySelector('table[data-bsp-student-table="true"]') ||
                (/Showing\s+\d+\s+to\s+\d+\s+of\s+\d+\s+entries/i.test(bodyTxt) &&
                 !/Summative\s+Evaluation/i.test(bodyTxt) &&
                 !/Progress\s+Report\s+Card/i.test(bodyTxt));

            var hasSummative = /Summative\s+Evaluation/i.test(bodyTxt);
            var hasReportHeader = /Progress\s+Report|Holistic\s+Report|Mark\s*sheet|Banglarshiksha|Academic\s+Year|Evaluation/i.test(bodyTxt);
            var hasSheetContainer = !!doc.querySelector('section.sheet, .sheet, .print-page, .marksheet-page, #printableArea, #printArea');
            var hasModalOpen = !!document.querySelector('.modal.show, .modal.in, [role="dialog"]:not([hidden])');

            // Accurate computed visibility check so CSS-hidden .loading/.spinner elements never block rendering
            var spinnerVisible = false;
            var spinnerEls = doc.querySelectorAll('.loading, .spinner, .spinner-border, #loader, .dataTables_processing');
            for (var s = 0; s < spinnerEls.length; s++) {
                var el = spinnerEls[s];
                try {
                    var st = window.getComputedStyle(el);
                    var rect = el.getBoundingClientRect();
                    if (st.display !== 'none' && st.visibility !== 'hidden' && parseFloat(st.opacity || '1') > 0.05 &&
                        el.offsetParent !== null && rect.width > 4 && rect.height > 4) {
                        spinnerVisible = true;
                        break;
                    }
                } catch (e) {}
            }

            var imgs = Array.from(doc.querySelectorAll('img'));
            var pendingImgs = imgs.filter(function(img) {
                var src = (img.getAttribute('src') || '').trim();
                if (!src || /^data:image\/svg/i.test(src)) return false;
                return !img.complete && img.offsetParent !== null;
            }).length;

            var looksLikeMarksheet = !hasPortalError && (!hasStudentListTable || hasModalOpen) &&
                (hasSummative || hasSheetContainer || (hasReportHeader && bodyTxt.length > 250) || window.__bspPrintTriggered === true);

            return JSON.stringify({
                readyState: document.readyState || '',
                hasAuthForm: hasAuthForm,
                hasPortalError: hasPortalError,
                hasStudentListTable: hasStudentListTable,
                hasModalOpen: hasModalOpen,
                hasSummative: hasSummative,
                looksLikeMarksheet: looksLikeMarksheet,
                spinnerVisible: spinnerVisible,
                pendingImgs: pendingImgs,
                url: location.href,
                textLen: bodyTxt.length
            });
        })();
    """.trimIndent()

    fun extractSummativeMarksJs(fallbackRoll: String, fallbackName: String): String {
        val qRoll = JSONObject.quote(fallbackRoll)
        val qName = JSONObject.quote(fallbackName)
        return """
            (function() {
                var searchDocs = [document];
                Array.from(document.querySelectorAll('iframe')).forEach(function(fr) {
                    try {
                        if (fr.contentDocument && fr.contentDocument.body) searchDocs.push(fr.contentDocument);
                    } catch (e) {}
                });

                var bodyTxt = searchDocs.map(function(d) {
                    return (d.body && d.body.innerText) ? d.body.innerText : '';
                }).join('\n');

                var hasAuth = !!document.querySelector('input[type="password"], input[name*="captcha" i]');
                if (hasAuth) {
                    return JSON.stringify({ ok: false, sessionExpired: true, error: 'Portal session expired' });
                }
                if (/An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered/i.test(bodyTxt)) {
                    return JSON.stringify({ ok: false, dbError: true, error: 'Portal database error displayed on page' });
                }

                function normalizeDigits(s) {
                    var map = { '০':'0','১':'1','২':'2','৩':'3','৪':'4','৫':'5','৬':'6','৭':'7','৮':'8','৯':'9' };
                    return String(s || '').replace(/[০-৯]/g, function(ch) { return map[ch] || ch; });
                }

                function cleanVal(v) {
                    if (v === undefined || v === null) return '';
                    var s = normalizeDigits(v).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
                    if (/^[-–—*•]+${'$'}/.test(s) || /^n\/?a${'$'}/i.test(s) || /^nil${'$'}/i.test(s)) return '';
                    return s;
                }

                // STRICT MARK SANITIZER: ONLY accepts valid numeric marks or AB. NEVER returns subject names, labels, or grades!
                function sanitizeMarkToken(raw, maxAllowed) {
                    var s = cleanVal(raw);
                    if (!s) return '';
                    if (/bengali|english|environment|math|health|physical|art|work\s*ed|science|history|geography|sanskrit|hindi|urdu|computer|subject|learning|total|grand|summative|formative|first|second|third|evaluation|obtained|full\s*marks|max\s*marks|grade|remark|paribesh|ganit|swasthya|বাং|ইং/i.test(s)) {
                        return '';
                    }
                    if (/^(?:ab|absent|abs)$/i.test(s)) return 'AB';
                    if (/^[a-e][+-]?$/i.test(s)) return '';

                    var slashMatch = s.match(/^(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)${'$'}/);
                    if (slashMatch) {
                        var numS = parseFloat(slashMatch[1]);
                        var denS = parseFloat(slashMatch[2]);
                        var limitS = maxAllowed || 600;
                        if (!isNaN(numS) && isFinite(numS) && numS >= 0 && numS <= limitS && denS >= numS) {
                            return String(numS).replace(/\.0+${'$'}/, '');
                        }
                        return '';
                    }

                    if (!/^\d+(?:\.\d+)?$/.test(s)) {
                        return '';
                    }
                    var n = parseFloat(s);
                    var limit = maxAllowed || 600;
                    if (isNaN(n) || !isFinite(n) || n < 0 || n > limit) return '';
                    return String(n).replace(/\.0+${'$'}/, '');
                }

                function isNonSubjectLabel(text) {
                    var s = cleanVal(text).toLowerCase();
                    if (!s || s.length < 2) return true;
                    if (/^\d+(?:\.\d+)?${'$'}/.test(s)) return true;
                    if (/^(?:sl|s\.?\s*no\.?|#)${'$'}/.test(s)) return true;
                    if (/\b(?:grand\s*total|sub\s*total|aggregate|total|percentage|position|rank|result|grade|division|remarks?|attendance|working\s*days|present|teacher|headmaster|guardian|signature|date\s*of|summative|formative|lpcd|bco|rubric|indicator|full\s*marks|obtained|marks?\s*obtained)\b/i.test(s)) {
                        return true;
                    }
                    if (/^(?:subject|learning\s*area|subjects?|विषय|বিষয়)/i.test(s)) return true;
                    return false;
                }

                function resolveSubjectDef(subjText) {
                    var raw = cleanVal(subjText);
                    if (!raw || isNonSubjectLabel(raw)) return null;
                    var s = raw.toLowerCase();
                    if (/bengali|1st\s*lang|first\s*lang|\bbn\b|\bben\b|বাংলা/.test(s)) {
                        return { key: 'ben', code: 'BEN', displayName: raw };
                    }
                    if (/english|2nd\s*lang|second\s*lang|\ben\b|\beng\b/.test(s)) {
                        return { key: 'eng', code: 'ENG', displayName: raw };
                    }
                    if (/environment\s*(?:&|and)\s*geo|geography|ভূগোল/.test(s)) {
                        return { key: 'geo', code: 'GEO', displayName: raw };
                    }
                    if (/environment\s*(?:&|and)\s*hist|history|ইতিহাস/.test(s)) {
                        return { key: 'hist', code: 'HIST', displayName: raw };
                    }
                    if (/environment|our\s*env|\bevs\b|paribesh|poribesh|পরিবেশ/.test(s)) {
                        return { key: 'evs', code: 'EVS', displayName: raw };
                    }
                    if (/physical\s*science|phys\.?\s*sci|ভৌত/.test(s)) {
                        return { key: 'psci', code: 'P.SCI', displayName: raw };
                    }
                    if (/life\s*science|life\s*sci|জীবন\s*বিজ্ঞান/.test(s)) {
                        return { key: 'lsci', code: 'L.SCI', displayName: raw };
                    }
                    if (/general\s*science|\bscience\b|বিজ্ঞান/.test(s)) {
                        return { key: 'sci', code: 'SCI', displayName: raw };
                    }
                    if (/math|ganit|arithmetic|গণিত/.test(s)) {
                        return { key: 'math', code: 'MATH', displayName: raw };
                    }
                    if (/health|physical\s*ed|hygiene|swasthya|স্বাস্থ্য/.test(s)) {
                        return { key: 'health', code: 'HEALTH', displayName: raw };
                    }
                    if (/art|work\s*ed|drawing|craft|music|কর্ম|কলা/.test(s)) {
                        return { key: 'art', code: 'ART', displayName: raw };
                    }
                    if (/sanskrit|সংস্কৃত/.test(s)) {
                        return { key: 'sans', code: 'SANS', displayName: raw };
                    }
                    if (/hindi|হিন্দি/.test(s)) {
                        return { key: 'hin', code: 'HIN', displayName: raw };
                    }
                    if (/urdu|উর্দু/.test(s)) {
                        return { key: 'urd', code: 'URD', displayName: raw };
                    }
                    if (/3rd\s*lang|third\s*lang/.test(s)) {
                        return { key: 'lang3', code: '3RD.L', displayName: raw };
                    }
                    if (/computer|ict|কম্পিউটার/.test(s)) {
                        return { key: 'comp', code: 'COMP', displayName: raw };
                    }
                    if (/ability\s*to\s*communicate|communicate/.test(s)) {
                        return { key: 'comm', code: 'COMM', displayName: raw };
                    }
                    if (/ability\s*to\s*correlate|correlate/.test(s)) {
                        return { key: 'corr', code: 'CORR', displayName: raw };
                    }
                    if (/problem\s*solv/.test(s)) {
                        return { key: 'prob', code: 'PROB', displayName: raw };
                    }
                    if (/coordination/.test(s)) {
                        return { key: 'coord', code: 'COORD', displayName: raw };
                    }

                    // Dynamic custom fallback key & compact readable code
                    var asciiWords = raw.replace(/[^A-Za-z0-9\s]/g, ' ').trim().split(/\s+/).filter(Boolean);
                    var key = asciiWords.length > 0
                        ? asciiWords.join('_').toLowerCase().slice(0, 18)
                        : ('sub_' + Math.abs(raw.split('').reduce(function(a, b) { return ((a << 5) - a + b.charCodeAt(0)) | 0; }, 0)));
                    var code = '';
                    if (asciiWords.length === 1) {
                        code = asciiWords[0].toUpperCase().slice(0, 6);
                    } else if (asciiWords.length >= 2) {
                        code = asciiWords.map(function(w) { return w.slice(0, 3).toUpperCase(); }).join('').slice(0, 7);
                    } else {
                        code = raw.slice(0, 6).toUpperCase();
                    }
                    return { key: key, code: code, displayName: raw };
                }

                // Extract Student Name & Roll fallback from marksheet text if available
                var detectedName = $qName;
                var detectedRoll = $qRoll;
                var nameM = bodyTxt.match(/(?:Student(?:'s)?\s*Name|Name\s*of\s*Student)\s*[:\-]\s*([^\n\r]+)/i);
                if (nameM && nameM[1] && cleanVal(nameM[1])) detectedName = cleanVal(nameM[1]);
                var rollM = bodyTxt.match(/(?:Roll\s*(?:No\.?|Number)?)\s*[:\-]\s*([0-9A-Za-z\/\-]+)/i);
                if (rollM && rollM[1] && cleanVal(rollM[1])) detectedRoll = cleanVal(rollM[1]);

                // Locate the innermost leaf Summative Evaluation table (strictly avoid Formative/LPCD/BCO)
                var allTables = [];
                searchDocs.forEach(function(d) {
                    allTables = allTables.concat(Array.from(d.querySelectorAll('table')));
                });
                var targetTable = null;
                var bestScore = -1;

                for (var t = 0; t < allTables.length; t++) {
                    var tbl = allTables[t];
                    var txt = (tbl.innerText || '').toLowerCase();
                    var prevTxt = '';
                    var p = tbl.previousElementSibling;
                    for (var hops = 0; hops < 3 && p; hops++) {
                        prevTxt += ' ' + (p.innerText || '').toLowerCase();
                        p = p.previousElementSibling;
                    }
                    var parentTxt = (tbl.parentElement ? tbl.parentElement.innerText.slice(0, 350) : '').toLowerCase();
                    var combined = prevTxt + ' ' + parentTxt + ' ' + txt;

                    var score = 0;
                    if (/summative\s+evaluation/.test(combined)) score += 16;
                    if (/1st\s*(?:summative|term)|first\s*(?:summative|term)/.test(txt)) score += 4;
                    if (/2nd\s*(?:summative|term)|second\s*(?:summative|term)/.test(txt)) score += 4;
                    if (/3rd\s*(?:summative|term)|third\s*(?:summative|term)/.test(txt)) score += 4;

                    var subjHits = 0;
                    var trs = Array.from(tbl.querySelectorAll('tr'));
                    for (var ri = 0; ri < trs.length; ri++) {
                        var firstCells = Array.from(trs[ri].querySelectorAll('th, td')).slice(0, 2);
                        if (firstCells.some(function(fc) { return !!resolveSubjectDef(fc.innerText); })) {
                            subjHits++;
                        }
                    }
                    score += Math.min(subjHits, 8) * 3;
                    if (/(?:formative\s+evaluation|\blpcd\b|\bbco\b|rubric|level\s+of\s+performance)/.test(combined) && !/summative\s+evaluation/.test(combined)) {
                        score -= 20;
                    }
                    if (tbl.querySelector('table')) score -= 12;
                    if (subjHits === 0) score -= 10;

                    if (score > bestScore && score >= 8) {
                        bestScore = score;
                        targetTable = tbl;
                    }
                }

                if (!targetTable) {
                    return JSON.stringify({
                        ok: false,
                        sessionExpired: false,
                        error: 'Summative Evaluation table not found on marksheet view.'
                    });
                }

                // Separate header rows cleanly BEFORE any data/subject row begins
                var allRows = Array.from(targetTable.querySelectorAll('tr'));
                var headerRows = [];
                var dataStartIdx = 0;
                for (var r = 0; r < allRows.length; r++) {
                    var row = allRows[r];
                    var rowCells = Array.from(row.querySelectorAll('th, td'));
                    var rowCellTxts = rowCells.map(function(c) { return cleanVal(c.innerText); });
                    var rowHasSubject = rowCells.some(function(c, ci) {
                        return ci < 3 && parseInt(c.getAttribute('colspan') || '1', 10) <= 2 && !!resolveSubjectDef(c.innerText);
                    });
                    if (rowHasSubject) {
                        dataStartIdx = r;
                        break;
                    }
                    var isHeadRow = !!row.closest('thead') || !!row.querySelector('th') || r < 2 ||
                        /subject|learning\s*area|summative|term|1st|2nd|3rd|first|second|third|obtained|full\s*marks|\bf\.?\s*m\.?\b|se\s*\(/i.test(rowCellTxts.join(' '));
                    if (isHeadRow && headerRows.length < 6) {
                        headerRows.push(row);
                        dataStartIdx = r + 1;
                    } else {
                        dataStartIdx = r;
                        break;
                    }
                }
                var bodyDataRows = allRows.slice(dataStartIdx);

                // Parse header grid preserving colSpan/rowSpan
                var grid = [];
                for (var hr = 0; hr < headerRows.length; hr++) grid[hr] = [];
                headerRows.forEach(function(row, rIdx) {
                    var cells = Array.from(row.querySelectorAll('th, td'));
                    var cCursor = 0;
                    cells.forEach(function(cell) {
                        while (grid[rIdx][cCursor] !== undefined) cCursor++;
                        var rs = parseInt(cell.getAttribute('rowspan') || '1', 10) || 1;
                        var cs = parseInt(cell.getAttribute('colspan') || '1', 10) || 1;
                        var label = cleanVal(cell.innerText).toLowerCase();
                        for (var rr = 0; rr < rs; rr++) {
                            if (rIdx + rr < grid.length) {
                                for (var cc = 0; cc < cs; cc++) {
                                    grid[rIdx + rr][cCursor + cc] = { text: label, colSpan: cs };
                                }
                            }
                        }
                        cCursor += cs;
                    });
                });

                // Pre-scan body rows expanding simple colspans for exact column alignment
                var parsedBodyRows = bodyDataRows.map(function(row) {
                    var rawCells = Array.from(row.querySelectorAll('td, th'));
                    var cells = [];
                    rawCells.forEach(function(el) {
                        var cs = Math.min(parseInt(el.getAttribute('colspan') || '1', 10) || 1, 8);
                        var txt = cleanVal(el.innerText);
                        cells.push(txt);
                        for (var k = 1; k < cs; k++) cells.push('');
                    });
                    return { row: row, cells: cells };
                }).filter(function(item) { return item.cells.length >= 2; });

                // Identify Subject column index reliably from actual body subject rows
                var subjectCol = -1;
                for (var br = 0; br < parsedBodyRows.length && subjectCol === -1; br++) {
                    var cs = parsedBodyRows[br].cells;
                    for (var ci = 0; ci < Math.min(3, cs.length); ci++) {
                        if (resolveSubjectDef(cs[ci])) {
                            subjectCol = ci;
                            break;
                        }
                    }
                }
                if (subjectCol === -1) subjectCol = 0;

                // Collect valid subject rows and summary/total rows
                var matchedSubjectRows = [];
                var printedRowTotal = { t1: '', t2: '', t3: '', grandTotal: '', percentage: '', position: '' };
                var totalSummaryCells = null;

                parsedBodyRows.forEach(function(item) {
                    var cells = item.cells;
                    var rowJoined = cells.join(' ').toLowerCase();
                    var def = null;
                    var rowSubjIdx = -1;
                    for (var k = 0; k <= Math.min(subjectCol + 1, cells.length - 1); k++) {
                        def = resolveSubjectDef(cells[k]);
                        if (def) {
                            rowSubjIdx = k;
                            break;
                        }
                    }

                    if (!def) {
                        if (/grand\s*total|\btotal\b/.test(cells.slice(0, 3).join(' ').toLowerCase())) {
                            totalSummaryCells = cells;
                        }
                        if (/percent|%/.test(rowJoined)) {
                            var pm = rowJoined.match(/(\d+(?:\.\d+)?)\s*%/);
                            if (pm) printedRowTotal.percentage = pm[1];
                        }
                        if (/position|rank/.test(rowJoined)) {
                            var rm = rowJoined.match(/(?:position|rank)\s*[:\-]?\s*(\d+)/i);
                            if (rm) printedRowTotal.position = rm[1];
                        }
                        return;
                    }

                    matchedSubjectRows.push({
                        def: def,
                        subjColIdx: rowSubjIdx,
                        cells: cells
                    });
                });

                var maxCols = Math.max(
                    grid.reduce(function(acc, r) { return Math.max(acc, r.length); }, 0),
                    matchedSubjectRows.reduce(function(acc, r) { return Math.max(acc, r.cells.length); }, 0)
                );

                function extractMaxFromHeaderText(txt) {
                    if (!txt) return 0;
                    var m = txt.match(/(?:full\s*marks?|f\.?\s*m\.?|max|se|term|\()\s*[:\-]?\s*(\d{1,3})\b/i);
                    if (m) {
                        var v = parseInt(m[1], 10);
                        if (v >= 5 && v <= 200) return v;
                    }
                    return 0;
                }

                var colMeta = [];
                var fmCols = {};
                var gradeCols = {};
                var colIndices = { subject: subjectCol, t1: -1, t2: -1, t3: -1, total: -1 };
                var headerMaxByTerm = { t1: 0, t2: 0, t3: 0 };

                for (var c = 0; c < maxCols; c++) {
                    var narrowParts = [];
                    var specificLeaf = '';
                    var groupText = '';
                    for (var rr = 0; rr < grid.length; rr++) {
                        var gCell = grid[rr][c];
                        if (!gCell || !gCell.text) continue;
                        if (gCell.colSpan <= 3) narrowParts.push(gCell.text);
                        if (gCell.colSpan <= 4) groupText += ' ' + gCell.text;
                    }
                    for (var rl = grid.length - 1; rl >= 0; rl--) {
                        if (grid[rl][c] && grid[rl][c].text && grid[rl][c].colSpan <= 2) {
                            specificLeaf = grid[rl][c].text;
                            break;
                        }
                    }
                    var narrowStack = narrowParts.join(' ').trim();
                    if (/subject|learning\s*area|বিষয়/.test(narrowStack) && c <= 2) {
                        subjectCol = c;
                        colIndices.subject = c;
                        continue;
                    }
                    if (c <= subjectCol) continue;

                    if (/^(?:sl\.?|s\.?\s*no\.?|#)${'$'}/.test(narrowStack)) continue;
                    if (/grade|গ্রেড/.test(specificLeaf) || (/\bgrade\b/.test(narrowStack) && !/obtained|marks?/.test(narrowStack))) {
                        gradeCols[c] = true;
                        continue;
                    }

                    var isExplicitFm = /full\s*marks?|\bf\.?\s*m\.?\b|max\s*marks?|পূর্ণমান|^se\s*\(\d+\)${'$'}/i.test(specificLeaf) ||
                        (/full\s*marks?|\bf\.?\s*m\.?\b/.test(narrowStack) && !/obtained/.test(specificLeaf));
                    if (isExplicitFm) {
                        fmCols[c] = true;
                    }

                    var termTag = '';
                    if (/\b1st\b|\bfirst\b|\bs1\b|\bse\s*1\b|\bt1\b|\bi\b\s*summative/.test(groupText)) termTag = 't1';
                    else if (/\b2nd\b|\bsecond\b|\bs2\b|\bse\s*2\b|\bt2\b|\bii\b\s*summative/.test(groupText)) termTag = 't2';
                    else if (/\b3rd\b|\bthird\b|\bs3\b|\bse\s*3\b|\bt3\b|\biii\b\s*summative/.test(groupText)) termTag = 't3';
                    else if (/\btotal\b|grand\s*total|aggregate/.test(narrowStack)) termTag = 'total';

                    var hMax = extractMaxFromHeaderText(specificLeaf) || extractMaxFromHeaderText(groupText);
                    if (termTag === 't1' && hMax > 0 && headerMaxByTerm.t1 === 0) headerMaxByTerm.t1 = hMax;
                    if (termTag === 't2' && hMax > 0 && headerMaxByTerm.t2 === 0) headerMaxByTerm.t2 = hMax;
                    if (termTag === 't3' && hMax > 0 && headerMaxByTerm.t3 === 0) headerMaxByTerm.t3 = hMax;

                    colMeta.push({
                        c: c,
                        termTag: termTag,
                        specificLeaf: specificLeaf,
                        narrowStack: narrowStack,
                        isExplicitFm: isExplicitFm,
                        isObtainedLabel: /obtained|marks?\s*obtained|\bobt\.?\b|\bscore\b|\bmarks?\b/i.test(specificLeaf)
                    });
                }

                // Inspect actual column contents to prevent picking F.M. or text columns
                function inspectColumnStats(colIdx) {
                    var validCount = 0;
                    var alphaCount = 0;
                    var values = [];
                    matchedSubjectRows.forEach(function(mr) {
                        var raw = colIdx < mr.cells.length ? mr.cells[colIdx] : '';
                        if (!raw) return;
                        var m = sanitizeMarkToken(raw, 200);
                        if (m && m !== 'AB') {
                            validCount++;
                            values.push(parseFloat(m));
                        } else if (m === 'AB') {
                            validCount++;
                        } else if (/[a-zA-Z\u0980-\u09FF]/.test(raw)) {
                            alphaCount++;
                        }
                    });
                    var uniqNums = {};
                    values.forEach(function(v) { uniqNums[String(v)] = true; });
                    var uniqKeys = Object.keys(uniqNums);
                    var looksLikeConstFm = values.length >= 3 && uniqKeys.length === 1 &&
                        [10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 75, 80, 90, 100].indexOf(parseFloat(uniqKeys[0])) >= 0;
                    var maxObserved = values.length ? Math.max.apply(null, values) : 0;
                    return {
                        validCount: validCount,
                        alphaCount: alphaCount,
                        looksLikeConstFm: looksLikeConstFm,
                        uniqCount: uniqKeys.length,
                        maxObserved: maxObserved
                    };
                }

                // Disambiguate columns within each termTag (F.M. vs Obtained Marks)
                var fmColForTerm = { t1: -1, t2: -1, t3: -1 };
                ['t1', 't2', 't3'].forEach(function(tag) {
                    var candidates = colMeta.filter(function(m) { return m.termTag === tag && !gradeCols[m.c]; });
                    if (candidates.length === 0) return;

                    var explicitFm = candidates.find(function(m) { return m.isExplicitFm; });
                    var explicitObt = candidates.find(function(m) { return m.isObtainedLabel && !m.isExplicitFm; });
                    if (explicitFm) fmColForTerm[tag] = explicitFm.c;

                    if (explicitObt) {
                        colIndices[tag] = explicitObt.c;
                        return;
                    }

                    // Filter out explicit F.M. columns; if 2 non-grade columns exist (left=F.M., right=Marks), prefer non-const right column
                    var nonFmCandidates = candidates.filter(function(m) { return !fmCols[m.c]; });
                    if (nonFmCandidates.length === 1) {
                        colIndices[tag] = nonFmCandidates[0].c;
                    } else if (nonFmCandidates.length >= 2) {
                        var scored = nonFmCandidates.map(function(m, idx) {
                            var st = inspectColumnStats(m.c);
                            var s = st.validCount * 3 - st.alphaCount * 5;
                            if (st.looksLikeConstFm && idx < nonFmCandidates.length - 1) s -= 20;
                            if (m.isObtainedLabel) s += 8;
                            if (idx === nonFmCandidates.length - 1) s += 2;
                            return { c: m.c, score: s, isConst: st.looksLikeConstFm };
                        }).sort(function(a, b) { return b.score - a.score; });

                        colIndices[tag] = scored[0].c;
                        if (fmColForTerm[tag] === -1 && nonFmCandidates[0].c !== scored[0].c) {
                            fmColForTerm[tag] = nonFmCandidates[0].c;
                            fmCols[nonFmCandidates[0].c] = true;
                        }
                    }
                });

                var totalCandidate = colMeta.find(function(m) {
                    return m.termTag === 'total' && !fmCols[m.c] && !gradeCols[m.c];
                });
                if (totalCandidate) colIndices.total = totalCandidate.c;

                // Positional fallback strictly to the right of subjectCol if header groupings weren't matched
                if (matchedSubjectRows.length > 0 && (colIndices.t1 === -1 || colIndices.t2 === -1 || colIndices.t3 === -1)) {
                    var numericMarkCols = [];
                    var detectedFmCols = [];
                    for (var bc = subjectCol + 1; bc < maxCols; bc++) {
                        if (fmCols[bc] || gradeCols[bc]) {
                            if (fmCols[bc]) detectedFmCols.push(bc);
                            continue;
                        }
                        var stats = inspectColumnStats(bc);
                        if (stats.validCount > 0 && stats.alphaCount <= stats.validCount) {
                            if (stats.looksLikeConstFm && bc + 1 < maxCols && inspectColumnStats(bc + 1).validCount > 0) {
                                fmCols[bc] = true;
                                detectedFmCols.push(bc);
                            } else {
                                numericMarkCols.push(bc);
                            }
                        }
                    }
                    if (numericMarkCols.length >= 3) {
                        if (colIndices.t1 === -1) colIndices.t1 = numericMarkCols[0];
                        if (colIndices.t2 === -1) colIndices.t2 = numericMarkCols[1];
                        if (colIndices.t3 === -1) colIndices.t3 = numericMarkCols[2];
                        if (colIndices.total === -1 && numericMarkCols.length >= 4) colIndices.total = numericMarkCols[3];
                    }
                }

                // Extract printed total row using final resolved column indices
                if (totalSummaryCells) {
                    if (colIndices.t1 > subjectCol && totalSummaryCells[colIndices.t1]) {
                        printedRowTotal.t1 = sanitizeMarkToken(totalSummaryCells[colIndices.t1], 1000);
                    }
                    if (colIndices.t2 > subjectCol && totalSummaryCells[colIndices.t2]) {
                        printedRowTotal.t2 = sanitizeMarkToken(totalSummaryCells[colIndices.t2], 1200);
                    }
                    if (colIndices.t3 > subjectCol && totalSummaryCells[colIndices.t3]) {
                        printedRowTotal.t3 = sanitizeMarkToken(totalSummaryCells[colIndices.t3], 1500);
                    }
                    if (colIndices.total > subjectCol && totalSummaryCells[colIndices.total]) {
                        printedRowTotal.grandTotal = sanitizeMarkToken(totalSummaryCells[colIndices.total], 2500);
                    }
                }

                var orderedSubjects = [];
                var subjectsMap = {};

                matchedSubjectRows.forEach(function(mr) {
                    var cells = mr.cells;
                    var rowSubjCol = mr.subjColIdx;

                    var fmT1 = (fmColForTerm.t1 > rowSubjCol && fmColForTerm.t1 < cells.length)
                        ? (parseFloat(sanitizeMarkToken(cells[fmColForTerm.t1], 200)) || 0) : 0;
                    var fmT2 = (fmColForTerm.t2 > rowSubjCol && fmColForTerm.t2 < cells.length)
                        ? (parseFloat(sanitizeMarkToken(cells[fmColForTerm.t2], 200)) || 0) : 0;
                    var fmT3 = (fmColForTerm.t3 > rowSubjCol && fmColForTerm.t3 < cells.length)
                        ? (parseFloat(sanitizeMarkToken(cells[fmColForTerm.t3], 200)) || 0) : 0;

                    var maxT1 = fmT1 || headerMaxByTerm.t1 || 20;
                    var maxT2 = fmT2 || headerMaxByTerm.t2 || 30;
                    var maxT3 = fmT3 || headerMaxByTerm.t3 || 50;
                    var maxTot = (fmT1 + fmT2 + fmT3 > 0) ? (fmT1 + fmT2 + fmT3) : (maxT1 + maxT2 + maxT3);

                    var t1Val = (colIndices.t1 > rowSubjCol && colIndices.t1 < cells.length)
                        ? sanitizeMarkToken(cells[colIndices.t1], Math.max(maxT1, 100)) : '';
                    var t2Val = (colIndices.t2 > rowSubjCol && colIndices.t2 < cells.length)
                        ? sanitizeMarkToken(cells[colIndices.t2], Math.max(maxT2, 100)) : '';
                    var t3Val = (colIndices.t3 > rowSubjCol && colIndices.t3 < cells.length)
                        ? sanitizeMarkToken(cells[colIndices.t3], Math.max(maxT3, 100)) : '';
                    var totVal = (colIndices.total > rowSubjCol && colIndices.total < cells.length)
                        ? sanitizeMarkToken(cells[colIndices.total], Math.max(maxTot, 250)) : '';

                    // Last-resort row-level scan strictly excluding F.M. and grade columns
                    if (!t1Val && !t2Val && !t3Val) {
                        var afterSubjNums = [];
                        for (var rc = rowSubjCol + 1; rc < cells.length; rc++) {
                            if (fmCols[rc] || gradeCols[rc]) continue;
                            var tok = sanitizeMarkToken(cells[rc], 200);
                            if (tok) afterSubjNums.push(tok);
                        }
                        if (afterSubjNums.length >= 3) {
                            t1Val = sanitizeMarkToken(afterSubjNums[0], Math.max(maxT1, 100));
                            t2Val = sanitizeMarkToken(afterSubjNums[1], Math.max(maxT2, 100));
                            t3Val = sanitizeMarkToken(afterSubjNums[2], Math.max(maxT3, 100));
                            totVal = afterSubjNums[3] ? sanitizeMarkToken(afterSubjNums[3], Math.max(maxTot, 250)) : '';
                        }
                    }

                    // De-duplicate same subject key if table had repeated sections cleanly
                    var finalKey = mr.def.key;
                    var finalCode = mr.def.code;
                    if (subjectsMap[finalKey]) {
                        var suffix = 2;
                        while (subjectsMap[finalKey + '_' + suffix]) suffix++;
                        finalKey = finalKey + '_' + suffix;
                    }

                    orderedSubjects.push({
                        key: finalKey,
                        code: finalCode,
                        displayName: mr.def.displayName,
                        maxT1: maxT1,
                        maxT2: maxT2,
                        maxT3: maxT3,
                        maxTotal: maxTot
                    });
                    subjectsMap[finalKey] = {
                        t1: t1Val,
                        t2: t2Val,
                        t3: t3Val,
                        total: totVal
                    };
                });

                function formatMaxHeaderLabel(prefix, keyName) {
                    var uniq = [];
                    orderedSubjects.forEach(function(s) {
                        var v = s[keyName];
                        if (v > 0 && uniq.indexOf(v) === -1) uniq.push(v);
                    });
                    var suffix = uniq.length > 0 ? uniq.join('/') : '-';
                    return prefix + ' (' + suffix + ')';
                }

                if (!printedRowTotal.percentage) {
                    var pmBody = bodyTxt.match(/(?:Percentage|%\s*of\s*Marks)\s*[:\-]?\s*(\d+(?:\.\d+)?)/i);
                    if (pmBody) printedRowTotal.percentage = pmBody[1];
                }
                if (!printedRowTotal.position) {
                    var posBody = bodyTxt.match(/(?:Position|Rank\s*in\s*Class)\s*[:\-]?\s*(\d+)/i);
                    if (posBody) printedRowTotal.position = posBody[1];
                }

                return JSON.stringify({
                    ok: true,
                    sessionExpired: false,
                    rollNo: detectedRoll,
                    studentName: detectedName,
                    matchedSubjectCount: orderedSubjects.length,
                    orderedSubjects: orderedSubjects,
                    subjects: subjectsMap,
                    termHeaders: {
                        term1Header: formatMaxHeaderLabel('1ST SUMMATIVE', 'maxT1'),
                        term2Header: formatMaxHeaderLabel('2ND SUMMATIVE', 'maxT2'),
                        term3Header: formatMaxHeaderLabel('3RD SUMMATIVE', 'maxT3')
                    },
                    printed: printedRowTotal
                });
            })();
        """.trimIndent()
    }

    fun tryCloseInPageMarksheetOrModalJs(): String = """
        (function() {
            var closeSelectors = [
                '.modal.show .close',
                '.modal.show .btn-close',
                '.modal.show [data-dismiss="modal"]',
                '.modal.show [data-bs-dismiss="modal"]',
                '.modal.in .close',
                '.modal.in [data-dismiss="modal"]',
                '[role="dialog"]:not([hidden]) button.close',
                '[role="dialog"]:not([hidden]) .btn-close'
            ];
            for (var i = 0; i < closeSelectors.length; i++) {
                var btn = document.querySelector(closeSelectors[i]);
                if (btn && btn.offsetParent !== null) {
                    btn.click();
                    return JSON.stringify({ handled: true, mode: 'modal_closed' });
                }
            }

            var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
            var onMarksheetView = /Summative\s+Evaluation|Progress\s+Report|Holistic\s+Report|Mark\s*sheet/i.test(bodyTxt) ||
                !!document.querySelector('section.sheet, .sheet, .print-page, .marksheet-page, #printableArea, #printArea');
            var onStudentList = !!document.querySelector('table[data-bsp-student-table="true"]') ||
                /Showing\s+\d+\s+to\s+\d+\s+of\s+\d+\s+entries/i.test(bodyTxt);

            if (onMarksheetView && !onStudentList) {
                var actionButtons = Array.from(document.querySelectorAll('a, button'));
                for (var j = 0; j < actionButtons.length; j++) {
                    var el = actionButtons[j];
                    var txt = (el.innerText || el.title || '').trim().toLowerCase();
                    if (/^(back|back\s*to\s*list|return|close)$/.test(txt) && el.offsetParent !== null) {
                        el.click();
                        return JSON.stringify({ handled: true, mode: 'back_button_clicked' });
                    }
                }
                return JSON.stringify({ handled: false, onMarksheetView: true });
            }
            return JSON.stringify({ handled: false, onMarksheetView: false });
        })();
    """.trimIndent()

    fun clickPaginationPageJs(targetPage: Int, mode: String = "next"): String = """
        (function() {
            var currentActive = document.querySelector('.dataTables_paginate .paginate_button.active, .pagination li.active a, .pagination li.active span, .paginate_button.current');
            var currentPage = currentActive ? (parseInt((currentActive.innerText || '').trim(), 10) || 1) : 1;

            if ($targetPage > 0 && currentPage === $targetPage && '$mode' === 'exact') {
                return JSON.stringify({ ok: true, alreadyOnPage: true, page: currentPage });
            }

            var firstRowBefore = '';
            var tbl = document.querySelector('table[data-bsp-student-table="true"]') || document.querySelector('table');
            if (tbl) {
                var firstRow = tbl.querySelector('tbody tr');
                if (firstRow) firstRowBefore = (firstRow.innerText || '').trim();
            }

            if ('$mode' === 'exact' && $targetPage > 0) {
                var numBtns = Array.from(document.querySelectorAll('.dataTables_paginate .paginate_button a, .dataTables_paginate .paginate_button, .pagination li a, .pagination li button'));
                var exactBtn = numBtns.find(function(b) {
                    return (b.innerText || '').trim() === String($targetPage) && !/disabled/i.test(b.className || '');
                });
                if (exactBtn) {
                    exactBtn.click();
                    return JSON.stringify({ ok: true, clicked: true, mode: 'exact', prevPage: currentPage, prevFirstRow: firstRowBefore });
                }
                if (currentPage < $targetPage) {
                    var stepNext = document.querySelector('.dataTables_paginate .paginate_button.next:not(.disabled) a, .dataTables_paginate .paginate_button.next:not(.disabled), li.next:not(.disabled) a, a.next:not(.disabled), button.next:not([disabled])');
                    if (stepNext) {
                        var snParent = (stepNext.parentElement && stepNext.parentElement.className) || '';
                        if (!/disabled/i.test((stepNext.className || '') + ' ' + snParent)) {
                            stepNext.click();
                            return JSON.stringify({ ok: true, clicked: true, mode: 'step_next', prevPage: currentPage, prevFirstRow: firstRowBefore });
                        }
                    }
                } else if (currentPage > $targetPage) {
                    var stepPrev = document.querySelector('.dataTables_paginate .paginate_button.previous:not(.disabled) a, .dataTables_paginate .paginate_button.previous:not(.disabled), li.previous:not(.disabled) a, a.previous:not(.disabled), button.previous:not([disabled])');
                    if (stepPrev) {
                        var spParent = (stepPrev.parentElement && stepPrev.parentElement.className) || '';
                        if (!/disabled/i.test((stepPrev.className || '') + ' ' + spParent)) {
                            stepPrev.click();
                            return JSON.stringify({ ok: true, clicked: true, mode: 'step_prev', prevPage: currentPage, prevFirstRow: firstRowBefore });
                        }
                    }
                }
            }

            var nextBtn = document.querySelector('.dataTables_paginate .paginate_button.next:not(.disabled) a, .dataTables_paginate .paginate_button.next:not(.disabled), li.next:not(.disabled) a, a.next:not(.disabled), button.next:not([disabled])');
            if (nextBtn) {
                var parentCls = (nextBtn.parentElement && nextBtn.parentElement.className) || '';
                if (!/disabled/i.test((nextBtn.className || '') + ' ' + parentCls)) {
                    nextBtn.click();
                    return JSON.stringify({ ok: true, clicked: true, mode: 'next', prevPage: currentPage, prevFirstRow: firstRowBefore });
                }
            }

            return JSON.stringify({ ok: false, error: 'Next page button not found or disabled' });
        })();
    """.trimIndent()

    fun restoreHrpcFiltersJs(snapshotJson: String): String {
        val qSnapshot = JSONObject.quote(snapshotJson)
        return """
            (function() {
                if (window.__bspRestoringFilters) {
                    return JSON.stringify({ triggeredSearch: true, alreadyRestoring: true });
                }
                var snap = JSON.parse($qSnapshot || '{}');

                function findSelect(patterns) {
                    var selects = Array.from(document.querySelectorAll('select'));
                    for (var p = 0; p < patterns.length; p++) {
                        var rx = patterns[p];
                        for (var i = 0; i < selects.length; i++) {
                            var s = selects[i];
                            var lbl = '';
                            if (s.id) {
                                var l = document.querySelector('label[for="' + s.id + '"]');
                                if (l) lbl = l.innerText || '';
                            }
                            if (!lbl && s.closest('.form-group, td, div')) {
                                lbl = (s.closest('.form-group, td, div').innerText || '').split('\n')[0] || '';
                            }
                            var meta = ((s.name || '') + ' ' + (s.id || '') + ' ' + lbl).toLowerCase();
                            if (rx.test(meta)) return s;
                        }
                    }
                    return null;
                }

                function setSelectValue(patterns, wanted) {
                    if (!wanted || (!wanted.value && !wanted.text)) return { found: false, changed: false, missingOption: false };
                    var s = findSelect(patterns);
                    if (!s) return { found: false, changed: false, missingOption: false };
                    if (wanted.value && s.value === wanted.value) return { found: true, changed: false, missingOption: false };
                    var opts = Array.from(s.options || []);
                    var match = opts.find(function(o) { return wanted.value && o.value === wanted.value; }) ||
                        opts.find(function(o) { return wanted.text && (o.text || '').trim().toLowerCase() === wanted.text.toLowerCase(); });
                    if (match && s.value !== match.value) {
                        s.value = match.value;
                        s.dispatchEvent(new Event('change', { bubbles: true }));
                        if (window.jQuery) {
                            try { window.jQuery(s).val(match.value).trigger('change'); } catch(e) {}
                        }
                        return { found: true, changed: true, missingOption: false };
                    }
                    return { found: true, changed: false, missingOption: !match && !!(wanted.value || wanted.text) };
                }

                function findSearchBtn() {
                    var btns = Array.from(document.querySelectorAll('button, input[type="submit"], input[type="button"], a.btn'));
                    return btns.find(function(b) {
                        var txt = (b.innerText || b.value || '').trim().toLowerCase();
                        return /^(search|show|submit|get\s*details|find)${'$'}/.test(txt);
                    }) || btns.find(function(b) {
                        var txt = (b.innerText || b.value || '').trim().toLowerCase();
                        return /\b(search|show|get\s*details)\b/.test(txt) && !/reset|clear|back|logout|cancel/.test(txt);
                    });
                }

                var hasTableRows = !!document.querySelector('table tbody tr td') &&
                    /Showing\s+\d+\s+to\s+\d+/i.test((document.body && document.body.innerText) || '');

                var rYear = setSelectValue([/academic|session|year/], snap.academicYear);
                var rMed = setSelectValue([/medium/], snap.medium);
                var rCls = setSelectValue([/\bclass\b|grade/], snap.className);
                var rSec = setSelectValue([/section/], snap.section);
                var changed = rYear.changed || rMed.changed || rCls.changed || rSec.changed;

                if (changed || !hasTableRows) {
                    var searchBtn = findSearchBtn();
                    if (searchBtn) {
                        window.__bspRestoringFilters = true;
                        var attempts = 0;
                        function waitCascadeAndClick() {
                            attempts++;
                            if (rMed.missingOption) rMed = setSelectValue([/medium/], snap.medium);
                            if (rCls.missingOption) rCls = setSelectValue([/\bclass\b|grade/], snap.className);
                            if (rSec.missingOption) rSec = setSelectValue([/section/], snap.section);

                            var stillWaitingAjax = (rMed.missingOption || rCls.missingOption || rSec.missingOption) && attempts < 10;
                            if (stillWaitingAjax) {
                                setTimeout(waitCascadeAndClick, 180);
                                return;
                            }
                            var btnNow = findSearchBtn();
                            window.__bspRestoringFilters = false;
                            if (btnNow) btnNow.click();
                        }
                        setTimeout(waitCascadeAndClick, changed ? 320 : 120);
                        return JSON.stringify({ triggeredSearch: true, changed: changed });
                    }
                }
                return JSON.stringify({ triggeredSearch: false, changed: changed, hasTableRows: hasTableRows });
            })();
        """.trimIndent()
    }

    fun inspectHprcFillContextJs(): String = """
        (function() {
            var docs = [document];
            Array.from(document.querySelectorAll('iframe')).forEach(function(fr) {
                try {
                    if (fr.contentDocument && fr.contentDocument.body) docs.push(fr.contentDocument);
                } catch (e) {}
            });

            var activeDoc = document;
            var bestTable = null;
            var bestWritableCount = 0;

            function isWritableMarkCtrl(el) {
                if (!el || el.disabled || el.readOnly) return false;
                if (el.offsetParent === null) return false;
                if (el.tagName === 'SELECT') return true;
                if (el.tagName === 'INPUT') {
                    var t = (el.type || 'text').toLowerCase();
                    if (t !== 'text' && t !== 'number' && t !== 'tel' && t !== '') return false;
                    var meta = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.className || '')).toLowerCase();
                    if (/search|filter|captcha|password|login/.test(meta)) return false;
                    return true;
                }
                return false;
            }

            docs.forEach(function(d) {
                Array.from(d.querySelectorAll('table')).forEach(function(tbl) {
                    var ctrls = Array.from(tbl.querySelectorAll('tbody input, tbody select, tr input, tr select')).filter(isWritableMarkCtrl);
                    if (ctrls.length > bestWritableCount) {
                        bestWritableCount = ctrls.length;
                        bestTable = tbl;
                        activeDoc = d;
                    }
                });
            });

            function selectedLabel(patterns) {
                for (var di = 0; di < docs.length; di++) {
                    var selects = Array.from(docs[di].querySelectorAll('select'));
                    for (var pi = 0; pi < patterns.length; pi++) {
                        for (var si = 0; si < selects.length; si++) {
                            var s = selects[si];
                            var lbl = '';
                            if (s.id) {
                                var l = docs[di].querySelector('label[for="' + s.id + '"]');
                                if (l) lbl = l.innerText || '';
                            }
                            var meta = ((s.name || '') + ' ' + (s.id || '') + ' ' + lbl).toLowerCase();
                            if (patterns[pi].test(meta) && s.selectedIndex >= 0) {
                                var opt = s.options[s.selectedIndex];
                                var txt = opt ? (opt.text || '').trim() : '';
                                if (txt && !/^--?\s*select/i.test(txt)) return txt;
                            }
                        }
                    }
                }
                return '';
            }

            var pageText = docs.map(function(d) { return (d.body && d.body.innerText) || ''; }).join('\n');
            var urlLower = (location.href || '').toLowerCase();
            var detectedCategory = '';
            if (/formative|\bfee?\b/.test(urlLower) || /formative\s+evaluation/i.test(pageText)) {
                detectedCategory = 'Formative';
            } else if (/lpcd/.test(urlLower) || /\blpcd\b|qualitative\s+indicator/i.test(pageText)) {
                detectedCategory = 'LPCD';
            } else if (/bco/.test(urlLower) || /\bbco\b|co-?curricular|part\s*[-:]?\s*a/i.test(pageText)) {
                detectedCategory = 'BCO';
            } else if (/summative|marks_entry|mark_entry/.test(urlLower) || /summative\s+evaluation/i.test(pageText)) {
                detectedCategory = 'Summative';
            }

            var headerLabels = [];
            if (bestTable) {
                var headThs = Array.from(bestTable.querySelectorAll('thead tr:last-child th, thead th, tr:first-child th'));
                headerLabels = headThs.map(function(th) { return (th.innerText || '').replace(/\s+/g, ' ').trim(); });
            }

            var rows = [];
            if (bestTable) {
                var bodyRows = Array.from(bestTable.querySelectorAll('tbody tr'));
                if (bodyRows.length === 0) bodyRows = Array.from(bestTable.querySelectorAll('tr')).slice(1);

                bodyRows.forEach(function(tr, rIdx) {
                    var ctrls = Array.from(tr.querySelectorAll('input, select')).filter(isWritableMarkCtrl);
                    if (ctrls.length === 0) return;

                    var tds = Array.from(tr.querySelectorAll('td'));
                    var roll = '';
                    var studentCode = '';
                    var studentName = '';

                    tds.forEach(function(td, ci) {
                        if (td.querySelector('input, select')) return;
                        var t = (td.innerText || '').replace(/\s+/g, ' ').trim();
                        if (!t) return;
                        if (!studentCode && /^\d{10,18}${'$'}/.test(t)) {
                            studentCode = t;
                        } else if (!roll && /^\d{1,4}${'$'}/.test(t) && ci <= 2) {
                            roll = t;
                        } else if (!studentName && /[A-Za-z]{2,}/.test(t) && !/^(male|female|yes|no|pass|fail)$/i.test(t)) {
                            studentName = t;
                        }
                    });

                    var fields = ctrls.map(function(el, fi) {
                        var cell = el.closest('td');
                        var colIdx = cell ? Array.from(tr.children).indexOf(cell) : -1;
                        var hdr = (colIdx >= 0 && colIdx < headerLabels.length) ? headerLabels[colIdx] : ('Col ' + (fi + 1));
                        return {
                            fieldIdx: fi,
                            label: hdr || el.name || el.id || ('Field ' + (fi + 1)),
                            tag: el.tagName,
                            max: el.getAttribute('max') || '',
                            currentVal: el.value || ''
                        };
                    });

                    rows.push({
                        rowIndex: rIdx,
                        roll: roll,
                        studentCode: studentCode,
                        studentName: studentName,
                        fields: fields
                    });
                });
            }

            return JSON.stringify({
                ok: true,
                detected: bestWritableCount > 0,
                category: detectedCategory || 'Summative',
                academicYear: selectedLabel([/academic|session|year/]),
                medium: selectedLabel([/medium/]),
                className: selectedLabel([/\bclass\b|grade/]),
                section: selectedLabel([/section/]),
                subject: selectedLabel([/subject|language/]),
                term: selectedLabel([/term|summative|evaluation/]),
                portalUrl: location.href,
                writableRowCount: rows.length,
                rows: rows
            });
        })();
    """.trimIndent()

    fun applyHprcFillPlanJs(planJson: String): String {
        val qPlan = JSONObject.quote(planJson)
        return """
            (function() {
                var plan = JSON.parse($qPlan || '[]');
                var docs = [document];
                Array.from(document.querySelectorAll('iframe')).forEach(function(fr) {
                    try {
                        if (fr.contentDocument && fr.contentDocument.body) docs.push(fr.contentDocument);
                    } catch (e) {}
                });

                function isWritableMarkCtrl(el) {
                    if (!el || el.disabled || el.readOnly) return false;
                    if (el.offsetParent === null) return false;
                    if (el.tagName === 'SELECT') return true;
                    if (el.tagName === 'INPUT') {
                        var t = (el.type || 'text').toLowerCase();
                        if (t !== 'text' && t !== 'number' && t !== 'tel' && t !== '') return false;
                        var meta = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.className || '')).toLowerCase();
                        if (/search|filter|captcha|password|login/.test(meta)) return false;
                        return true;
                    }
                    return false;
                }

                var bestTable = null;
                var bestCount = 0;
                docs.forEach(function(d) {
                    Array.from(d.querySelectorAll('table')).forEach(function(tbl) {
                        var c = Array.from(tbl.querySelectorAll('tbody input, tbody select, tr input, tr select')).filter(isWritableMarkCtrl).length;
                        if (c > bestCount) { bestCount = c; bestTable = tbl; }
                    });
                });

                if (!bestTable) {
                    return JSON.stringify({ ok: false, filledFields: 0, error: 'Writable portal marks table not found.' });
                }

                function setCtrlVal(el, val) {
                    if (el.tagName === 'SELECT') {
                        var opts = Array.from(el.options || []);
                        var target = opts.find(function(o) {
                            return o.value.toLowerCase() === String(val).toLowerCase() ||
                                (o.text || '').trim().toLowerCase() === String(val).trim().toLowerCase();
                        });
                        if (!target) return false;
                        el.value = target.value;
                    } else {
                        var maxAttr = parseFloat(el.getAttribute('max') || '');
                        var numV = parseFloat(val);
                        if (!isNaN(maxAttr) && !isNaN(numV) && numV > maxAttr) return false;
                        var proto = Object.getPrototypeOf(el);
                        var setter = Object.getOwnPropertyDescriptor(proto, 'value') && Object.getOwnPropertyDescriptor(proto, 'value').set;
                        if (setter) setter.call(el, String(val)); else el.value = String(val);
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    el.dispatchEvent(new Event('blur', { bubbles: true }));
                    return true;
                }

                var bodyRows = Array.from(bestTable.querySelectorAll('tbody tr'));
                if (bodyRows.length === 0) bodyRows = Array.from(bestTable.querySelectorAll('tr')).slice(1);
                var dataTrs = bodyRows.filter(function(tr) {
                    return Array.from(tr.querySelectorAll('input, select')).some(isWritableMarkCtrl);
                });

                var filledFields = 0;
                var updatedRows = 0;

                plan.forEach(function(entry) {
                    var tr = dataTrs[entry.rowIndex];
                    if (!tr) return;
                    var ctrls = Array.from(tr.querySelectorAll('input, select')).filter(isWritableMarkCtrl);
                    var rowChanged = false;
                    (entry.writes || []).forEach(function(w) {
                        var el = ctrls[w.fieldIdx];
                        if (el && setCtrlVal(el, w.value)) {
                            filledFields++;
                            rowChanged = true;
                        }
                    });
                    if (rowChanged) {
                        updatedRows++;
                        tr.style.outline = '2px solid #16a34a';
                    }
                });

                // Strictly DO NOT click any Save / Submit button automatically.
                return JSON.stringify({
                    ok: true,
                    updatedRows: updatedRows,
                    filledFields: filledFields
                });
            })();
        """.trimIndent()
    }
}
