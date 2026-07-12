# -*- coding: utf-8 -*-
"""
KupuProxy for exteraGram
MTProto proxy finder with self-update.
"""

from __future__ import annotations

import json
import os
import re
import socket
import threading
import time
import traceback
import weakref
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any, Dict, List, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from base_plugin import BasePlugin, MenuItemData, MenuItemType, MethodHook
from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from ui.alert import AlertDialogBuilder
from ui.settings import Divider, Header, Input, Switch, Text

try:
    from hook_utils import get_private_field
except Exception:  # pragma: no cover
    get_private_field = None  # type: ignore

try:
    from ui.bulletin import BulletinHelper
except Exception:  # pragma: no cover
    BulletinHelper = None  # type: ignore

try:
    from java import jclass, dynamic_proxy
    from java.lang import Integer, Long
except Exception:  # pragma: no cover
    jclass = None  # type: ignore
    dynamic_proxy = None  # type: ignore
    Integer = None  # type: ignore
    Long = None  # type: ignore


# --- Plugin Metadata (AST-parsed, keep static) ---
__id__ = "kupu_proxy"
__name__ = "KupuProxy"
__description__ = (
    "Поиск и проверка MTProto-прокси (как KupuProxy Android).\n"
    "На экране «Прокси» — строка **KupuProxy** (TextCheckCell, как в Proxy Tools).\n"
    "Команды: `.kupu auto` · `.kupu chat` · `.kupu add` · `.kupu del` · `.kupu update`\n"
    "Источники: SoliSpirit, Yagami200, Kort, Argh94…\n"
    "Самообновление с GitHub."
)
__author__ = "@Kirillka645"
__version__ = "1.1.6"
__icon__ = "exteraPlugins/1"
__min_version__ = "11.9.1"
# 1.4.0 / 1.4.3.3 — ок; не требуем 1.4.3.10
__sdk_version__ = ">=1.4.0"


PLUGIN_ID = "kupu_proxy"
GITHUB_REPO = "Kirillka645/KupuProxy"
UPDATE_URL = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
RAW_PLUGIN_MIRRORS = [
    f"https://raw.githubusercontent.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
    f"https://cdn.jsdelivr.net/gh/{GITHUB_REPO}@main/exteragram/kupu_proxy.py",
    f"https://raw.githack.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
    f"https://ghproxy.net/https://raw.githubusercontent.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
]

SOURCES: List[Dict[str, Any]] = [
    {
        "id": "solispirit",
        "name": "SoliSpirit",
        "urls": [
            "https://fastly.jsdelivr.net/gh/SoliSpirit/mtproto@master/all_proxies.txt",
            "https://raw.githack.com/SoliSpirit/mtproto/master/all_proxies.txt",
            "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt",
        ],
    },
    {
        "id": "yagami",
        "name": "Yagami200",
        "urls": [
            "https://raw.githubusercontent.com/Yagami200/free-mtproto-proxies/main/all_proxies.txt",
            "https://raw.githubusercontent.com/Yagami200/free-mtproto-proxies/main/proxies.json",
            "https://cdn.jsdelivr.net/gh/Yagami200/free-mtproto-proxies@main/proxies.json",
            "https://cdn.jsdelivr.net/gh/Yagami200/free-mtproto-proxies@main/all_proxies.txt",
        ],
    },
    {
        "id": "kort",
        "name": "Kort All",
        "urls": [
            "https://cdn.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_all.txt",
            "https://raw.githack.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
            "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
        ],
    },
    {
        "id": "argh94",
        "name": "Argh94 Scraper",
        "urls": [
            "https://raw.githubusercontent.com/Argh94/telegram-proxy-scraper/main/proxy.txt",
            "https://cdn.jsdelivr.net/gh/Argh94/telegram-proxy-scraper@main/proxy.txt",
            "https://raw.githack.com/Argh94/telegram-proxy-scraper/main/proxy.txt",
        ],
    },
    {
        "id": "surfboard",
        "name": "Surfboard",
        "urls": [
            "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies.txt",
            "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies-tested.txt",
            "https://raw.githubusercontent.com/Surfboardv2ray/TGProto/main/proxies-tested.txt",
        ],
    },
    {
        "id": "aliila",
        "name": "ALIILAPRO",
        "urls": [
            "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt",
            "https://cdn.jsdelivr.net/gh/ALIILAPRO/MTProtoProxy@main/mtproto.txt",
        ],
    },
]

LINK_RE = re.compile(
    r"(?:tg://proxy|https?://t\.me/proxy)\?[^\s<>'\"`)\]#,]+",
    re.IGNORECASE,
)
VERSION_RE = re.compile(r'^__version__\s*=\s*["\']([^"\']+)["\']', re.M)


def http_get(url: str, timeout: int = 18) -> str:
    req = Request(
        url,
        headers={
            "User-Agent": f"KupuProxy-exteraGram/{__version__}",
            "Accept": "*/*",
        },
    )
    with urlopen(req, timeout=timeout) as resp:  # noqa: S310
        data = resp.read()
    for enc in ("utf-8", "utf-8-sig", "latin-1"):
        try:
            return data.decode(enc)
        except Exception:
            continue
    return data.decode("utf-8", errors="ignore")


def parse_proxy_links(text: str) -> List[str]:
    found: List[str] = []
    seen = set()
    for m in LINK_RE.finditer(text or ""):
        raw = m.group(0).strip().rstrip("),];\"'")
        if raw.lower().startswith("https://t.me/proxy?"):
            raw = "tg://proxy?" + raw.split("?", 1)[1]
        elif raw.lower().startswith("http://t.me/proxy?"):
            raw = "tg://proxy?" + raw.split("?", 1)[1]
        key = raw
        if key not in seen:
            seen.add(key)
            found.append(raw)
    return found


def parse_proxy_url(url: str) -> Optional[Dict[str, Any]]:
    if not url or "proxy?" not in url:
        return None
    q = url.split("?", 1)[1]
    params: Dict[str, str] = {}
    for part in q.split("&"):
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        params[k] = v
    server = params.get("server")
    port = params.get("port")
    secret = params.get("secret")
    if not server or not port or not secret:
        return None
    try:
        port_i = int(port)
    except Exception:
        return None
    return {"server": server, "port": port_i, "secret": secret, "url": url}


def tcp_ping(server: str, port: int, timeout: float = 2.0) -> int:
    sock = None
    try:
        t0 = time.time()
        sock = socket.create_connection((server, port), timeout=timeout)
        ms = int((time.time() - t0) * 1000)
        return ms if ms > 0 else 1
    except Exception:
        return -1
    finally:
        try:
            if sock:
                sock.close()
        except Exception:
            pass


def version_tuple(v: str) -> Tuple[int, ...]:
    v = (v or "0").lstrip("vV")
    parts = []
    for p in v.split("."):
        try:
            parts.append(int(re.sub(r"\D", "", p) or "0"))
        except Exception:
            parts.append(0)
    return tuple(parts)


def is_newer(current: str, latest: str) -> bool:
    a, b = version_tuple(current), version_tuple(latest)
    n = max(len(a), len(b))
    a += (0,) * (n - len(a))
    b += (0,) * (n - len(b))
    return b > a


class KupuProxyPlugin(BasePlugin):
    def __init__(self):
        super().__init__()
        self._results: List[Dict[str, Any]] = []
        self._scanning = False
        self._lock = threading.Lock()
        self._drawer_id = None
        self._chat_id = None
        self._hooks = []
        self._ignore_native_trigger = False  # True пока сами пишем флаги
        # extra rows in ProxyListActivity adapter (как proxy_tools):
        # 0 shadow, 1 TextCheckCell KupuProxy, 2 TextSettingsCell auto, 3 shadow
        self._KUPU_EXTRA_ROWS = 4

    # region lifecycle

    def on_plugin_load(self):
        self.log(f"KupuProxy {__version__} loaded")
        try:
            self._drawer_id = self.add_menu_item(
                MenuItemData(
                    menu_type=MenuItemType.DRAWER_MENU,
                    text="KupuProxy",
                    subtext="Строка в меню прокси",
                    icon="msg_proxy",
                    on_click=self._on_drawer_click,
                    priority=50,
                )
            )
        except Exception as e:
            self.log(f"drawer menu failed: {e}")

        try:
            self._chat_id = self.add_menu_item(
                MenuItemData(
                    menu_type=MenuItemType.CHAT_ACTION_MENU,
                    text="KupuProxy",
                    subtext="Скан / auto",
                    icon="msg_proxy",
                    on_click=self._on_drawer_click,
                    priority=20,
                )
            )
        except Exception:
            pass

        try:
            self.add_on_send_message_hook(priority=5)
        except Exception as e:
            self.log(f"add_on_send_message_hook: {e}")

        # Как proxy_tools: вшиваем строки в ListAdapter (TextCheckCell)
        self._hook_proxy_list_ui()

        if self.get_setting("auto_update", True):
            threading.Thread(target=self._bg_check_update, daemon=True).start()

    def on_plugin_unload(self):
        self._scanning = False
        self.log("KupuProxy unloaded")

    # endregion

    # region ProxyListActivity rows (как proxy_tools.plugin)

    def _pf(self, obj, name: str):
        """get_private_field с fallbacks."""
        if get_private_field is not None:
            try:
                return get_private_field(obj, name)
            except Exception:
                pass
        try:
            return getattr(obj, name, None)
        except Exception:
            return None

    def _hook_proxy_list_ui(self):
        """
        Встраиваем KupuProxy в нативный список прокси через адаптер
        (тот же приём, что proxy_tools: +rows, TextCheckCell, OnItemClick).
        """
        if jclass is None or MethodHook is None or Integer is None:
            self.log("hook_proxy_list_ui: java/MethodHook unavailable")
            return

        try:
            ActivityCls = jclass("java.lang.Class").forName(
                "org.telegram.ui.ProxyListActivity"
            )
            AdapterCls = jclass("java.lang.Class").forName(
                "org.telegram.ui.ProxyListActivity$ListAdapter"
            )
        except Exception as e:
            self.log(f"ProxyListActivity classes: {e}")
            return

        extra = self._KUPU_EXTRA_ROWS
        plugin_ref = weakref.ref(self)

        class GetItemCountHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    prev = int(param.getResult())
                    param.setResult(Integer(prev + extra))
                except Exception:
                    pass

        def _outer_field(obj, name):
            if get_private_field is not None:
                try:
                    return get_private_field(obj, name)
                except Exception:
                    pass
            return getattr(obj, name, None)

        class GetItemViewTypeHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    act = _outer_field(param.thisObject, "this$0")
                    if not act:
                        return
                    orig_count = int(_outer_field(act, "rowCount"))
                    pos = int(param.args[0])
                    if pos < orig_count:
                        return
                    cpos = pos - orig_count
                    # 0,3 = ShadowSectionCell (type 0)
                    # 1 = TextCheckCell (type 3)
                    # 2 = TextSettingsCell (type 1) — «Сделать всё»
                    if cpos in (0, 3):
                        param.setResult(Integer(0))
                    elif cpos == 1:
                        param.setResult(Integer(3))
                    else:
                        param.setResult(Integer(1))
                except Exception:
                    pass

        class GetItemIdHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    act = _outer_field(param.thisObject, "this$0")
                    if not act:
                        return
                    orig_count = int(_outer_field(act, "rowCount"))
                    pos = int(param.args[0])
                    if pos >= orig_count and Long is not None:
                        param.setResult(Long(90000 + pos))
                except Exception:
                    pass

        class BindHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    act = _outer_field(param.thisObject, "this$0")
                    if not act:
                        return
                    orig_count = int(_outer_field(act, "rowCount"))
                    pos = int(param.args[1])
                    if pos >= orig_count:
                        param.setResult(None)
                except Exception:
                    pass

            def after_hooked_method(self, param):
                plugin = plugin_ref()
                if not plugin:
                    return
                try:
                    act = _outer_field(param.thisObject, "this$0")
                    if not act:
                        return
                    orig_count = int(_outer_field(act, "rowCount"))
                    pos = int(param.args[1])
                    if pos < orig_count:
                        return
                    cpos = pos - orig_count
                    cell = param.args[0].itemView
                    Theme = jclass("org.telegram.ui.ActionBar.Theme")

                    if cpos == 1:
                        on = bool(plugin.get_setting("kupu_switch_on", False))
                        try:
                            cell.setTextAndCheck("KupuProxy", on, True)
                        except Exception:
                            try:
                                cell.setTextAndCheck("KupuProxy", on, False)
                            except Exception as e:
                                plugin.log(f"setTextAndCheck: {e}")
                        try:
                            cell.setBackgroundColor(
                                Theme.getColor(Theme.key_windowBackgroundWhite)
                            )
                        except Exception:
                            pass
                    elif cpos == 2:
                        try:
                            cell.setText("Сделать всё", False)
                        except Exception as e:
                            plugin.log(f"setText: {e}")
                        try:
                            cell.setBackgroundColor(
                                Theme.getColor(Theme.key_windowBackgroundWhite)
                            )
                        except Exception:
                            pass
                except Exception as e:
                    try:
                        plugin.log(f"BindHook: {e}")
                    except Exception:
                        pass

        class CreateViewHook(MethodHook):
            def after_hooked_method(self, param):
                plugin = plugin_ref()
                if not plugin:
                    return
                act = param.thisObject
                try:
                    lv = _outer_field(act, "listView")
                    if lv is None:
                        return
                    old_listener = _outer_field(lv, "onItemClickListener")
                    try:
                        if old_listener and "KupuProxyClick" in str(
                            old_listener.getClass().getName()
                        ):
                            return
                    except Exception:
                        pass

                    ListenerBase = jclass(
                        "org.telegram.ui.Components.RecyclerListView$OnItemClickListener"
                    )

                    class KupuProxyClick(dynamic_proxy(ListenerBase)):
                        def __init__(self, old, pref, activity):
                            super().__init__()
                            self.old = old
                            self.pref = pref
                            self.activity = activity

                        def onItemClick(self, view, pos):
                            p = self.pref()
                            if not p:
                                return
                            try:
                                orig_count = int(
                                    _outer_field(self.activity, "rowCount")
                                )
                            except Exception:
                                if self.old:
                                    self.old.onItemClick(view, pos)
                                return

                            if pos >= orig_count:
                                cpos = pos - orig_count
                                if cpos == 1:
                                    cur = bool(p.get_setting("kupu_switch_on", False))
                                    nxt = not cur
                                    p._save_kupu_switch(nxt)
                                    try:
                                        view.setChecked(nxt)
                                    except Exception:
                                        pass
                                    if nxt:
                                        p._bulletin("KupuProxy: ищу рабочие…")
                                        p._one_tap_setup(None)
                                    else:
                                        p._bulletin("KupuProxy выкл")
                                elif cpos == 2:
                                    p._save_kupu_switch(True)
                                    p._one_tap_setup(None)
                            else:
                                if self.old:
                                    self.old.onItemClick(view, pos)

                    lv.setOnItemClickListener(
                        KupuProxyClick(old_listener, plugin_ref, act)
                    )
                    plugin.log("ProxyListActivity click listener OK")
                except Exception as e:
                    plugin.log(f"CreateViewHook: {e}\n{traceback.format_exc()}")

        for m in ActivityCls.getDeclaredMethods():
            try:
                if m.getName() == "createView":
                    self.hook_method(m, CreateViewHook())
            except Exception as e:
                self.log(f"hook createView: {e}")

        for m in AdapterCls.getDeclaredMethods():
            try:
                name = m.getName()
                if name == "getItemCount":
                    self.hook_method(m, GetItemCountHook())
                elif name == "getItemViewType":
                    self.hook_method(m, GetItemViewTypeHook())
                elif name == "getItemId":
                    self.hook_method(m, GetItemIdHook())
                elif name == "onBindViewHolder" and len(m.getParameterTypes()) == 2:
                    self.hook_method(m, BindHook())
            except Exception as e:
                self.log(f"hook adapter {m.getName()}: {e}")

        self.log("ProxyListActivity adapter hooks installed")

    def _save_kupu_switch(self, enabled: bool):
        try:
            if hasattr(self, "set_setting"):
                self.set_setting("kupu_switch_on", enabled)
        except Exception:
            pass
        try:
            from org.telegram.messenger import ApplicationLoader
            from android.content import Context

            ctx = ApplicationLoader.applicationContext
            prefs = ctx.getSharedPreferences("kupu_proxy_plugin", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("kupu_switch_on", bool(enabled)).apply()
        except Exception:
            pass

    def _safe_refresh_proxy_list(self):
        """Перерисовать список прокси (как proxy_tools._safe_refresh)."""

        def go():
            try:
                frag = get_last_fragment()
                if frag is None:
                    return
                name = str(frag.getClass().getName())
                if "ProxyList" not in name and "ProxySettings" not in name:
                    # всё равно пробуем adapter
                    pass
                for attr in ("listAdapter", "adapter"):
                    ad = self._pf(frag, attr)
                    if ad is not None and hasattr(ad, "notifyDataSetChanged"):
                        ad.notifyDataSetChanged()
                        break
                for meth in ("updateRows",):
                    m = getattr(frag, meth, None)
                    if callable(m):
                        try:
                            m()
                        except Exception:
                            pass
            except Exception as e:
                self.log(f"refresh list: {e}")

        run_on_ui_thread(go)

    def _one_tap_setup(self, dialog_id: Optional[int]):
        """
        Одной кнопкой:
        1) скачать списки  2) проверить  3) добавить рабочие
        4) удалить мёртвые  5) включить прокси + автопереключение
        6) выбрать лучший как текущий
        """
        if self._scanning:
            self._bulletin("Уже выполняется…")
            return

        self._scanning = True
        msg0 = "KupuProxy: делаю всё… скан → добавление → автопереключение"
        if dialog_id:
            self._reply(dialog_id, "⚡ " + msg0)
        else:
            self._bulletin(msg0)

        def work():
            try:
                # 1-2 scan
                proxies = self._fetch_all()
                if not proxies:
                    self._finish_one_tap(dialog_id, False, "Не удалось скачать списки")
                    return

                max_check = self._int_setting("max_check", 120, 20, 500)
                workers = self._int_setting("workers", 24, 4, 48)
                timeout = self._float_setting("timeout_sec", 2.0, 0.8, 5.0)
                stop_when = self._int_setting("stop_when", 20, 0, 100)

                import random

                random.shuffle(proxies)
                batch = proxies[:max_check]

                results: List[Dict[str, Any]] = []

                def check_one(url: str) -> Optional[Dict[str, Any]]:
                    info = parse_proxy_url(url)
                    if not info:
                        return None
                    ping = tcp_ping(info["server"], info["port"], timeout)
                    if ping < 0:
                        return None
                    return {
                        "url": info["url"] if str(info["url"]).startswith("tg://") else url,
                        "server": info["server"],
                        "port": info["port"],
                        "secret": info["secret"],
                        "ping": ping,
                    }

                with ThreadPoolExecutor(max_workers=workers) as ex:
                    futs = [ex.submit(check_one, u) for u in batch]
                    for fut in as_completed(futs):
                        try:
                            item = fut.result()
                        except Exception:
                            item = None
                        if item:
                            results.append(item)
                            results.sort(key=lambda x: x["ping"])
                            if stop_when and len(results) >= stop_when:
                                break

                with self._lock:
                    self._results = results

                if not results:
                    self._finish_one_tap(
                        dialog_id, False, "Рабочих прокси не найдено. Попробуйте снова."
                    )
                    return

                # 3 add all working
                added, skipped, err = self._add_all_to_telegram(results)

                # 4 delete dead from saved list
                try:
                    checked, deleted, kept = self._delete_dead_from_telegram()
                except Exception as e:
                    self.log(f"del in one-tap: {e}")
                    deleted = 0

                # 5-6 enable + rotation + best current
                best = results[0]
                self._enable_proxy_system(best)
                self._save_kupu_switch(True)
                try:
                    self._safe_refresh_proxy_list()
                except Exception:
                    pass

                summary = (
                    f"✅ KupuProxy готово!\n"
                    f"• рабочих: **{len(results)}**\n"
                    f"• добавлено: **{added}** (уже были: {skipped})\n"
                    f"• удалено мёртвых: **{deleted}**\n"
                    f"• текущий: `{best['server']}:{best['port']}` · {best['ping']} ms\n"
                    f"• прокси **вкл**, автопереключение **вкл**\n\n"
                    f"Если один упадёт — Telegram переключит на другой."
                )
                self._finish_one_tap(dialog_id, True, summary)
            except Exception as e:
                self.log(traceback.format_exc())
                self._finish_one_tap(dialog_id, False, f"Ошибка: {e}")
            finally:
                self._scanning = False

        threading.Thread(target=work, daemon=True).start()

    def _finish_one_tap(self, dialog_id: Optional[int], ok: bool, text: str):
        if dialog_id:
            self._reply(dialog_id, text)
        else:
            self._bulletin(text[:200], error=not ok)

        def dlg():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    return
                b = AlertDialogBuilder(act)
                b.set_title("KupuProxy" if ok else "KupuProxy — ошибка")
                b.set_message(text)
                b.set_positive_button("OK", None)
                b.show()
            except Exception as e:
                self.log(f"one-tap dialog: {e}")

        run_on_ui_thread(dlg)

    def _enable_proxy_system(self, best: Dict[str, Any]):
        """Включить нативные: прокси + автопереключение, выбрать лучший."""
        done = threading.Event()
        err_box: List[Exception] = []
        self._ignore_native_trigger = True

        def go():
            try:
                from org.telegram.messenger import SharedConfig
                from org.telegram.tgnet import ConnectionsManager

                proxy = self._make_proxy_info(
                    best["server"], int(best["port"]), best.get("secret") or ""
                )
                try:
                    SharedConfig.addProxy(proxy)
                except Exception:
                    pass
                SharedConfig.currentProxy = proxy

                for name in ("proxyEnabled", "useProxySettings"):
                    try:
                        setattr(SharedConfig, name, True)
                    except Exception:
                        pass

                for name in (
                    "proxyRotationEnabled",
                    "proxyAutoSwitch",
                    "useProxyRotation",
                ):
                    try:
                        setattr(SharedConfig, name, True)
                    except Exception:
                        pass

                try:
                    SharedConfig.saveProxyList()
                except Exception:
                    pass

                try:
                    from org.telegram.messenger import ApplicationLoader
                    from android.content import Context

                    ctx = ApplicationLoader.applicationContext
                    prefs = ctx.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                    ed = prefs.edit()
                    ed.putBoolean("proxy_enabled", True)
                    for key in (
                        "proxyRotationEnabled",
                        "proxy_rotation_enabled",
                        "proxyAutoSwitch",
                        "auto_proxy_switch",
                    ):
                        ed.putBoolean(key, True)
                    ed.apply()
                except Exception as e:
                    self.log(f"prefs rotation: {e}")

                try:
                    ConnectionsManager.setProxySettings(
                        True,
                        proxy.address,
                        proxy.port,
                        proxy.username,
                        proxy.password,
                        proxy.secret,
                    )
                except Exception as e:
                    self.log(f"setProxySettings: {e}")

                self._last_rotation = True
                self._notify_proxy_changed()
            except Exception as e:
                err_box.append(e)
            finally:
                done.set()

        try:
            run_on_ui_thread(go)
            done.wait(30)
            if err_box:
                raise err_box[0]
        finally:
            # отпустить guard после записи prefs
            def unlock():
                self._ignore_native_trigger = False

            try:
                run_on_ui_thread(unlock)
            except Exception:
                self._ignore_native_trigger = False

    # endregion

    # region settings

    def create_settings(self) -> List[Any]:
        return [
            Header(text="KupuProxy"),
            Text(
                text=f"Версия плагина: {__version__}",
                subtext="Самообновление с GitHub · Kirillka645/KupuProxy",
            ),
            Switch(
                key="auto_update",
                text="Проверять обновления при запуске",
                default=True,
            ),
            Switch(
                key="notify_results",
                text="Показывать bulletin о результатах",
                default=True,
            ),
            Switch(
                key="auto_on_rotation",
                text="Также автоскан при «Автопереключение»",
                default=False,
            ),
            Text(
                text="Настройки → Прокси → внизу списка «KupuProxy»",
                subtext="Нативная TextCheckCell (как Proxy Tools)",
            ),
            Divider(),
            Header(text="Скан"),
            Input(
                key="max_check",
                text="Макс. прокси на проверку",
                default="120",
            ),
            Input(
                key="workers",
                text="Параллельных проверок",
                default="24",
            ),
            Input(
                key="timeout_sec",
                text="TCP timeout (сек)",
                default="2.0",
            ),
            Input(
                key="stop_when",
                text="Стоп после N рабочих (0 = все)",
                default="20",
            ),
            Divider(),
            Header(text="Как пользоваться"),
            Text(
                text="Настройки → Прокси → KupuProxy / Сделать всё",
                subtext="Строки встроены в список (TextCheckCell)",
            ),
            Text(text="`.kupu auto` — то же из чата"),
            Text(text="`.kupu scan` / `.kupu chat` / `.kupu add` / `.kupu del`"),
            Text(text="`.kupu list` / `.kupu use 1` / `.kupu update`"),
            Divider(),
            Text(
                text="Источники",
                subtext="SoliSpirit · Yagami200 · Kort · Argh94 · Surfboard · ALIILAPRO",
            ),
        ]

    # endregion

    # region UI helpers

    def _bulletin(self, text: str, error: bool = False):
        def go():
            try:
                if BulletinHelper is not None:
                    if error:
                        BulletinHelper.show_error(text)
                    else:
                        BulletinHelper.show_info(text)
                else:
                    self.log(text)
            except Exception as e:
                self.log(f"bulletin: {e} | {text}")

        run_on_ui_thread(go)

    def _toast(self, text: str):
        def go():
            try:
                from android.widget import Toast
                from client_utils import get_last_fragment

                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if act:
                    Toast.makeText(act, text, Toast.LENGTH_SHORT).show()
            except Exception as e:
                self.log(f"toast: {e}")

        run_on_ui_thread(go)

    def _reply(self, dialog_id: int, text: str):
        """Best-effort reply in chat (chunks if long); falls back to bulletin."""
        chunks = self._chunk_text(text, 3500)
        ok_any = False
        for chunk in chunks:
            if self._send_text(dialog_id, chunk):
                ok_any = True
            else:
                break
        if not ok_any:
            self._bulletin(text[:180])

    @staticmethod
    def _chunk_text(text: str, limit: int = 3500) -> List[str]:
        if len(text) <= limit:
            return [text]
        parts: List[str] = []
        cur: List[str] = []
        size = 0
        for line in text.split("\n"):
            add = len(line) + 1
            if cur and size + add > limit:
                parts.append("\n".join(cur))
                cur = [line]
                size = add
            else:
                cur.append(line)
                size += add
        if cur:
            parts.append("\n".join(cur))
        return parts or [text[:limit]]

    def _send_text(self, dialog_id: int, text: str) -> bool:
        try:
            from client_utils import send_message

            for args in (
                (dialog_id, text),
                (text, dialog_id),
                (int(dialog_id), str(text)),
            ):
                try:
                    send_message(*args)
                    return True
                except TypeError:
                    continue
                except Exception as e:
                    self.log(f"send_message{args[:1]}: {e}")
                    continue
        except Exception as e:
            self.log(f"send_message import/fail: {e}")
        return False

    def _on_drawer_click(self, context: Dict[str, Any]):
        self._show_main_dialog()

    def _show_main_dialog(self):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    self._bulletin("Нет activity", error=True)
                    return

                builder = AlertDialogBuilder(act)
                builder.set_title("KupuProxy")
                n = len(self._results)
                builder.set_message(
                    f"v{__version__}\n"
                    f"Рабочих в кэше: {n}\n\n"
                    f"Настройки → Прокси → прокрути вниз:\n"
                    f"• **KupuProxy** (switch)\n"
                    f"• **Сделать всё**\n\n"
                    f"Или `.kupu auto` в чате."
                )
                builder.set_positive_button(
                    "⚡ Auto", lambda b, w: self._one_tap_setup(None)
                )
                builder.set_neutral_button("Скан", lambda b, w: self._start_scan(None))
                builder.set_negative_button("Топ", lambda b, w: self._show_top_dialog())
                builder.show()
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(f"UI error: {e}", error=True)

        run_on_ui_thread(go)

    def _show_top_dialog(self):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    return
                with self._lock:
                    items = list(self._results[:15])
                if not items:
                    self._bulletin("Сначала сделайте .kupu scan", error=True)
                    return

                labels = [
                    f"{i+1}. {p['server']}:{p['port']}  ·  {p['ping']} ms"
                    for i, p in enumerate(items)
                ]
                builder = AlertDialogBuilder(act)
                builder.set_title("Рабочие прокси")
                # setItems if available
                try:
                    builder.set_items(
                        labels,
                        lambda b, which: self._apply_proxy(items[which]["url"]),
                    )
                except Exception:
                    builder.set_message("\n".join(labels[:10]))
                    builder.set_positive_button(
                        "Подключить #1",
                        lambda b, w: self._apply_proxy(items[0]["url"]),
                    )
                builder.set_negative_button("Закрыть", None)
                builder.show()
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(str(e), error=True)

        run_on_ui_thread(go)

    # endregion

    # region commands

    def on_send_message_hook(self, account: int, params: Any):
        from base_plugin import HookResult, HookStrategy

        try:
            msg = getattr(params, "message", None)
            if not isinstance(msg, str):
                return HookResult()
            text = msg.strip()
            if not text.lower().startswith(".kupu"):
                return HookResult()

            # block sending command to chat
            dialog_id = getattr(params, "peer", None) or getattr(params, "dialog_id", None)
            try:
                # dialog id sometimes in other fields
                if dialog_id is None and hasattr(params, "peer"):
                    dialog_id = 0
            except Exception:
                dialog_id = 0

            # Prefer getting dialog from fragment
            try:
                frag = get_last_fragment()
                if frag and hasattr(frag, "getDialogId"):
                    dialog_id = int(frag.getDialogId())
            except Exception:
                pass

            threading.Thread(
                target=self._handle_command,
                args=(text, int(dialog_id or 0)),
                daemon=True,
            ).start()

            result = HookResult(strategy=HookStrategy.CANCEL)
            return result
        except Exception as e:
            self.log(f"cmd hook: {e}")
            return HookResult()

    def _handle_command(self, text: str, dialog_id: int):
        parts = text.strip().split()
        cmd = parts[1].lower() if len(parts) > 1 else "help"

        if cmd in ("help", "h", "?"):
            self._reply(
                dialog_id,
                "🔌 **KupuProxy** v"
                + __version__
                + "\n\n"
                + "⚡ Настройки → Прокси → **KupuProxy** / **Сделать всё**\n"
                + "`.kupu auto` — то же из чата\n\n"
                + "`.kupu scan` — скачать + проверить\n"
                + "`.kupu chat` — все рабочие ссылки в чат\n"
                + "`.kupu add` — добавить все рабочие в прокси Telegram\n"
                + "`.kupu del` — удалить нерабочие из списка прокси\n"
                + "`.kupu list` / `.kupu top` — кратко\n"
                + "`.kupu use N` — подключить №N\n"
                + "`.kupu update` — обновить плагин\n"
                + "`.kupu menu` — диалог\n",
            )
        elif cmd in ("auto", "all", "go", "setup"):
            self._one_tap_setup(dialog_id)
        elif cmd == "scan":
            self._start_scan(dialog_id)
        elif cmd == "chat":
            self._cmd_chat(dialog_id)
        elif cmd == "add":
            self._cmd_add(dialog_id)
        elif cmd == "del":
            self._cmd_del(dialog_id)
        elif cmd in ("list", "top"):
            self._cmd_list(dialog_id)
        elif cmd == "use":
            n = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 1
            self._cmd_use(dialog_id, n)
        elif cmd == "update":
            self._do_self_update(force=True, dialog_id=dialog_id)
        elif cmd == "menu":
            self._show_main_dialog()
        else:
            self._reply(dialog_id, f"Неизвестная команда: {cmd}\n`.kupu help`")

    def _cmd_list(self, dialog_id: int):
        with self._lock:
            items = list(self._results[:20])
        if not items:
            self._reply(dialog_id, "Пока пусто. Сделайте `.kupu scan`")
            return
        lines = [
            f"{i+1}. `{p['server']}:{p['port']}` — **{p['ping']} ms**"
            for i, p in enumerate(items)
        ]
        self._reply(
            dialog_id,
            f"✅ Рабочие ({len(items)}):\n"
            + "\n".join(lines)
            + "\n\n`.kupu chat` — все ссылки\n`.kupu use 1`",
        )

    def _cmd_chat(self, dialog_id: int):
        """Выдать в чат все рабочие прокси полными tg:// ссылками."""
        with self._lock:
            items = list(self._results)
        if not items:
            self._reply(dialog_id, "Пока пусто. Сначала `.kupu scan`")
            return

        header = f"🔌 KupuProxy — рабочие ({len(items)}):\n\n"
        lines = []
        for i, p in enumerate(items):
            url = p.get("url") or ""
            if not url.startswith("tg://"):
                url = (
                    f"tg://proxy?server={p['server']}&port={p['port']}"
                    f"&secret={p.get('secret', '')}"
                )
            lines.append(f"{i+1}. {url}  ({p['ping']} ms)")

        body = header + "\n".join(lines)
        self._reply(dialog_id, body)
        self._bulletin(f"Отправлено {len(items)} прокси в чат")

    def _cmd_add(self, dialog_id: int):
        """Добавить все рабочие из скана в список прокси Telegram."""
        with self._lock:
            items = list(self._results)
        if not items:
            self._reply(dialog_id, "Пока пусто. Сначала `.kupu scan`")
            return

        def work():
            try:
                added, skipped, err = self._add_all_to_telegram(items)
                msg = (
                    f"✅ В список прокси Telegram:\n"
                    f"• добавлено: **{added}**\n"
                    f"• уже были: **{skipped}**"
                )
                if err:
                    msg += f"\n• ошибки: {err}"
                msg += "\n\nНастройки → Данные и память → Прокси"
                self._reply(dialog_id, msg)
                self._bulletin(f"Добавлено прокси: {added}")
            except Exception as e:
                self.log(traceback.format_exc())
                self._reply(dialog_id, f"Ошибка add: {e}")

        self._reply(dialog_id, f"➕ Добавляю {len(items)} рабочих в список прокси…")
        threading.Thread(target=work, daemon=True).start()

    def _cmd_del(self, dialog_id: int):
        """Удалить нерабочие прокси из списка Telegram (TCP-check)."""

        def work():
            try:
                checked, deleted, kept = self._delete_dead_from_telegram()
                self._reply(
                    dialog_id,
                    f"🗑 Проверка списка прокси Telegram:\n"
                    f"• проверено: **{checked}**\n"
                    f"• удалено (недоступны): **{deleted}**\n"
                    f"• оставлено: **{kept}**",
                )
                self._bulletin(f"Удалено нерабочих: {deleted}")
            except Exception as e:
                self.log(traceback.format_exc())
                self._reply(dialog_id, f"Ошибка del: {e}")

        self._reply(dialog_id, "🧹 Проверяю сохранённые прокси, удаляю мёртвые…")
        threading.Thread(target=work, daemon=True).start()

    def _cmd_use(self, dialog_id: int, n: int):
        with self._lock:
            items = list(self._results)
        if n < 1 or n > len(items):
            self._reply(dialog_id, f"Нет прокси №{n}. Всего: {len(items)}")
            return
        p = items[n - 1]
        self._apply_proxy(p["url"])
        self._reply(
            dialog_id,
            f"Подключаю #{n}: `{p['server']}:{p['port']}` ({p['ping']} ms)",
        )

    # endregion

    # region scan

    def _start_scan(self, dialog_id: Optional[int]):
        if self._scanning:
            self._bulletin("Уже сканирую…")
            return
        self._scanning = True
        if dialog_id:
            self._reply(dialog_id, "🔎 KupuProxy: загрузка списков…")
        else:
            self._bulletin("KupuProxy: сканирование…")

        def work():
            try:
                proxies = self._fetch_all()
                if not proxies:
                    self._scanning = False
                    msg = "Не удалось скачать списки (сеть / блокировка)"
                    if dialog_id:
                        self._reply(dialog_id, msg)
                    else:
                        self._bulletin(msg, error=True)
                    return

                max_check = self._int_setting("max_check", 120, 20, 500)
                workers = self._int_setting("workers", 24, 4, 48)
                timeout = self._float_setting("timeout_sec", 2.0, 0.8, 5.0)
                stop_when = self._int_setting("stop_when", 20, 0, 100)

                # shuffle lightly for diversity
                import random

                random.shuffle(proxies)
                batch = proxies[:max_check]

                if dialog_id:
                    self._reply(
                        dialog_id,
                        f"📥 Загружено **{len(proxies)}** уникальных\n"
                        f"Проверяю **{len(batch)}** (×{workers})…",
                    )

                results: List[Dict[str, Any]] = []
                checked = 0
                stop = False

                def check_one(url: str) -> Optional[Dict[str, Any]]:
                    info = parse_proxy_url(url)
                    if not info:
                        return None
                    ping = tcp_ping(info["server"], info["port"], timeout)
                    if ping < 0:
                        return None
                    return {
                        "url": info["url"] if info["url"].startswith("tg://") else url,
                        "server": info["server"],
                        "port": info["port"],
                        "secret": info["secret"],
                        "ping": ping,
                    }

                with ThreadPoolExecutor(max_workers=workers) as ex:
                    futs = {ex.submit(check_one, u): u for u in batch}
                    for fut in as_completed(futs):
                        if stop:
                            break
                        checked += 1
                        try:
                            item = fut.result()
                        except Exception:
                            item = None
                        if item:
                            results.append(item)
                            results.sort(key=lambda x: x["ping"])
                            if stop_when and len(results) >= stop_when:
                                stop = True

                with self._lock:
                    self._results = results

                if dialog_id:
                    if results:
                        top = "\n".join(
                            f"{i+1}. `{r['server']}:{r['port']}` — **{r['ping']} ms**"
                            for i, r in enumerate(results[:10])
                        )
                        self._reply(
                            dialog_id,
                            f"✅ Готово: **{len(results)}** рабочих "
                            f"(проверено {checked})\n\n{top}\n\n"
                            f"Подключить: `.kupu use 1`",
                        )
                    else:
                        self._reply(
                            dialog_id,
                            f"❌ Рабочих нет (проверено {checked}). "
                            f"Попробуйте снова / другую сеть.",
                        )
                else:
                    self._bulletin(
                        f"KupuProxy: {len(results)} рабочих"
                        if results
                        else "KupuProxy: рабочих нет",
                        error=not results,
                    )
                    if results:
                        self._show_top_dialog()
            except Exception as e:
                self.log(traceback.format_exc())
                if dialog_id:
                    self._reply(dialog_id, f"Ошибка: {e}")
                else:
                    self._bulletin(str(e), error=True)
            finally:
                self._scanning = False

        threading.Thread(target=work, daemon=True).start()

    def _fetch_all(self) -> List[str]:
        all_links: List[str] = []
        seen = set()
        for src in SOURCES:
            for url in src["urls"]:
                try:
                    body = http_get(url)
                    links = parse_proxy_links(body)
                    if not links:
                        continue
                    for L in links:
                        if L not in seen:
                            seen.add(L)
                            all_links.append(L)
                    self.log(f"{src['name']}: +{len(links)} from {url}")
                    break  # next source
                except Exception as e:
                    self.log(f"fetch fail {url}: {e}")
                    continue
        return all_links

    def _int_setting(self, key: str, default: int, lo: int, hi: int) -> int:
        try:
            v = int(str(self.get_setting(key, default)).strip())
            return max(lo, min(hi, v))
        except Exception:
            return default

    def _float_setting(self, key: str, default: float, lo: float, hi: float) -> float:
        try:
            v = float(str(self.get_setting(key, default)).replace(",", ".").strip())
            return max(lo, min(hi, v))
        except Exception:
            return default

    # endregion

    # region apply proxy

    def _apply_proxy(self, url: str):
        if not url.startswith("tg://"):
            if url.startswith("https://t.me/proxy?"):
                url = "tg://proxy?" + url.split("?", 1)[1]
            else:
                self._bulletin("Некорректная ссылка", error=True)
                return

        def go():
            try:
                from android.content import Intent
                from android.net import Uri

                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    self._bulletin("Нет activity", error=True)
                    return

                # Native Telegram proxy dialog
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                act.startActivity(intent)
                self._bulletin("Открываю прокси в Telegram…")
            except Exception as e:
                self.log(traceback.format_exc())
                # fallback SharedConfig
                try:
                    self._apply_shared_config(url)
                except Exception as e2:
                    self._bulletin(f"Не удалось: {e2}", error=True)

        run_on_ui_thread(go)

    def _apply_shared_config(self, url: str):
        info = parse_proxy_url(url)
        if not info:
            raise RuntimeError("parse failed")
        from org.telegram.messenger import SharedConfig
        from org.telegram.tgnet import ConnectionsManager
        from org.telegram.messenger import NotificationCenter

        proxy = self._make_proxy_info(info["server"], int(info["port"]), info["secret"])
        SharedConfig.addProxy(proxy)
        SharedConfig.currentProxy = proxy
        SharedConfig.saveProxyList()
        try:
            SharedConfig.proxyEnabled = True
        except Exception:
            pass
        try:
            ConnectionsManager.setProxySettings(
                True,
                proxy.address,
                proxy.port,
                proxy.username,
                proxy.password,
                proxy.secret,
            )
        except Exception as e:
            self.log(f"setProxySettings: {e}")
        self._notify_proxy_changed()
        self._bulletin(f"Прокси {info['server']}:{info['port']} включён")

    def _make_proxy_info(self, server: str, port: int, secret: str):
        from org.telegram.messenger import SharedConfig

        # ProxyInfo(address, port, username, password, secret)
        try:
            return SharedConfig.ProxyInfo(server, int(port), "", "", secret or "")
        except Exception:
            # older constructors
            p = SharedConfig.ProxyInfo()
            p.address = server
            p.port = int(port)
            p.username = ""
            p.password = ""
            p.secret = secret or ""
            return p

    def _proxy_key(self, address: str, port: int, secret: str = "") -> str:
        return f"{(address or '').lower()}:{(port or 0)}:{(secret or '')}"

    def _iter_proxy_list(self):
        from org.telegram.messenger import SharedConfig

        pl = SharedConfig.proxyList
        items = []
        try:
            # ArrayList
            n = pl.size()
            for i in range(n):
                items.append(pl.get(i))
        except Exception:
            try:
                for p in pl:
                    items.append(p)
            except Exception as e:
                self.log(f"proxyList iterate: {e}")
        return items

    def _existing_proxy_keys(self) -> set:
        keys = set()
        for p in self._iter_proxy_list():
            try:
                addr = getattr(p, "address", None) or ""
                port = int(getattr(p, "port", 0) or 0)
                secret = getattr(p, "secret", None) or ""
                keys.add(self._proxy_key(addr, port, secret))
            except Exception:
                continue
        return keys

    def _add_all_to_telegram(self, items: List[Dict[str, Any]]) -> Tuple[int, int, int]:
        """Returns (added, skipped, errors)."""
        from org.telegram.messenger import SharedConfig

        existing = self._existing_proxy_keys()
        added = 0
        skipped = 0
        errors = 0

        def do_add():
            nonlocal added, skipped, errors
            for it in items:
                try:
                    server = it["server"]
                    port = int(it["port"])
                    secret = it.get("secret") or ""
                    key = self._proxy_key(server, port, secret)
                    if key in existing:
                        skipped += 1
                        continue
                    proxy = self._make_proxy_info(server, port, secret)
                    SharedConfig.addProxy(proxy)
                    existing.add(key)
                    added += 1
                except Exception as e:
                    errors += 1
                    self.log(f"addProxy fail: {e}")
            try:
                SharedConfig.saveProxyList()
            except Exception as e:
                self.log(f"saveProxyList: {e}")
            self._notify_proxy_changed()

        # SharedConfig usually must be touched on UI thread
        done = threading.Event()
        err_box: List[Exception] = []

        def go():
            try:
                do_add()
            except Exception as e:
                err_box.append(e)
            finally:
                done.set()

        run_on_ui_thread(go)
        done.wait(60)
        if err_box:
            raise err_box[0]
        return added, skipped, errors

    def _delete_dead_from_telegram(self) -> Tuple[int, int, int]:
        """TCP-check all saved proxies; delete dead. Returns (checked, deleted, kept)."""
        from org.telegram.messenger import SharedConfig

        timeout = self._float_setting("timeout_sec", 2.0, 0.8, 5.0)
        proxies = self._iter_proxy_list()
        checked = 0
        dead = []

        for p in proxies:
            try:
                addr = getattr(p, "address", None) or ""
                port = int(getattr(p, "port", 0) or 0)
                if not addr or port <= 0:
                    continue
                checked += 1
                ping = tcp_ping(addr, port, timeout)
                if ping < 0:
                    dead.append(p)
            except Exception as e:
                self.log(f"check saved proxy: {e}")

        deleted = 0
        done = threading.Event()
        err_box: List[Exception] = []

        def go():
            nonlocal deleted
            try:
                for p in dead:
                    try:
                        SharedConfig.deleteProxy(p)
                        deleted += 1
                    except Exception:
                        # fallback remove + save
                        try:
                            SharedConfig.proxyList.remove(p)
                            deleted += 1
                        except Exception as e2:
                            self.log(f"deleteProxy: {e2}")
                try:
                    SharedConfig.saveProxyList()
                except Exception:
                    pass
                self._notify_proxy_changed()
            except Exception as e:
                err_box.append(e)
            finally:
                done.set()

        run_on_ui_thread(go)
        done.wait(60)
        if err_box:
            raise err_box[0]
        kept = max(0, checked - deleted)
        return checked, deleted, kept

    def _notify_proxy_changed(self):
        try:
            from org.telegram.messenger import NotificationCenter

            NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.proxySettingsChanged
            )
        except Exception as e:
            self.log(f"proxySettingsChanged: {e}")

    # endregion

    # region self-update

    def _bg_check_update(self):
        time.sleep(3)
        try:
            self._do_self_update(force=False, dialog_id=None)
        except Exception as e:
            self.log(f"auto update: {e}")

    def _do_self_update(self, force: bool = False, dialog_id: Optional[int] = None):
        try:
            remote_ver, source_url, body = self._fetch_remote_plugin()
            if not remote_ver:
                if force:
                    msg = "Не удалось проверить обновление"
                    if dialog_id:
                        self._reply(dialog_id, msg)
                    else:
                        self._bulletin(msg, error=True)
                return

            if not is_newer(__version__, remote_ver):
                if force:
                    msg = f"Уже актуальная версия v{__version__}"
                    if dialog_id:
                        self._reply(dialog_id, msg)
                    else:
                        self._bulletin(msg)
                return

            # Write over this file
            path = os.path.abspath(__file__)
            # also try .plugin sibling
            candidates = [path]
            if path.endswith(".py"):
                candidates.append(path[:-3] + ".plugin")
            if path.endswith(".plugin"):
                candidates.append(path[:-7] + ".py")

            written = None
            for p in candidates:
                try:
                    if os.path.isfile(p) or p == path:
                        with open(p, "w", encoding="utf-8") as f:
                            f.write(body)
                        written = p
                        break
                except Exception as e:
                    self.log(f"write {p}: {e}")

            if not written:
                # save to Downloads
                dl = "/storage/emulated/0/Download/kupu_proxy.plugin"
                with open(dl, "w", encoding="utf-8") as f:
                    f.write(body)
                written = dl
                msg = (
                    f"⬇ Скачано v{remote_ver} → `{dl}`\n"
                    f"Откройте файл в exteraGram → Install plugin\n"
                    f"(не удалось перезаписать текущий файл)"
                )
            else:
                msg = (
                    f"✅ KupuProxy обновлён: **v{__version__} → v{remote_ver}**\n"
                    f"Файл: `{written}`\n"
                    f"Перезапустите exteraGram или выкл/вкл плагин."
                )

            if dialog_id:
                self._reply(dialog_id, msg)
            else:
                self._bulletin(f"KupuProxy → v{remote_ver}. Перезапустите клиент.")
                self._show_update_done_dialog(remote_ver, written)
            self.log(f"updated from {source_url}")
        except Exception as e:
            self.log(traceback.format_exc())
            if force:
                if dialog_id:
                    self._reply(dialog_id, f"Ошибка обновления: {e}")
                else:
                    self._bulletin(str(e), error=True)

    def _show_update_done_dialog(self, ver: str, path: str):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    return
                b = AlertDialogBuilder(act)
                b.set_title(f"Обновлено до v{ver}")
                b.set_message(
                    f"Плагин записан:\n{path}\n\n"
                    f"Перезапустите exteraGram, чтобы загрузить новую версию."
                )
                b.set_positive_button("OK", None)
                b.show()
            except Exception as e:
                self.log(f"update dialog: {e}")

        run_on_ui_thread(go)

    def _fetch_remote_plugin(self) -> Tuple[Optional[str], Optional[str], Optional[str]]:
        # 1) Try GitHub release asset named kupu_proxy.py / .plugin
        try:
            meta = http_get(UPDATE_URL, timeout=12)
            data = json.loads(meta)
            tag = (data.get("tag_name") or "").lstrip("v")
            # plugin version is independent of app version; look into assets or body
            assets = data.get("assets") or []
            for a in assets:
                name = a.get("name") or ""
                if name in ("kupu_proxy.py", "kupu_proxy.plugin", "KupuProxy.plugin"):
                    url = a.get("browser_download_url")
                    if url:
                        body = http_get(url, timeout=30)
                        m = VERSION_RE.search(body)
                        ver = m.group(1) if m else tag or None
                        return ver, url, body
        except Exception as e:
            self.log(f"release api: {e}")

        # 2) Raw mirrors of plugin source in repo
        for url in RAW_PLUGIN_MIRRORS:
            try:
                body = http_get(url, timeout=25)
                if "KupuProxyPlugin" not in body and "__id__" not in body:
                    continue
                m = VERSION_RE.search(body)
                if not m:
                    continue
                return m.group(1), url, body
            except Exception as e:
                self.log(f"raw fail {url}: {e}")
                continue
        return None, None, None

    # endregion
