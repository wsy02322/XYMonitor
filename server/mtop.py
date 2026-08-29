import hashlib
import json

APP_KEY = "34839810"
API = "mtop.idle.web.xyh.item.list"
VERSION = "1.0"
HOST = "h5api.m.goofish.com"


def md5(text: str) -> str:
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def sign(t: str, token: str, data: str) -> str:
    return md5(f"{token}&{t}&{APP_KEY}&{data}")


def request_data(user_id: str) -> str:
    return json.dumps(
        {
            "needGroupInfo": True,
            "pageNumber": 1,
            "userId": str(user_id),
            "pageSize": 20,
        },
        separators=(",", ":"),
        ensure_ascii=False,
    )


def ret_text(root: dict) -> str:
    ret = root.get("ret")
    if ret is None:
        return ""
    if isinstance(ret, list):
        return " | ".join(str(item) for item in ret)
    return str(ret)


def is_success(root: dict) -> bool:
    return "SUCCESS" in ret_text(root)


def is_token_error(root: dict) -> bool:
    text = ret_text(root)
    return (
        "FAIL_SYS_TOKEN_EMPTY" in text
        or "FAIL_SYS_TOKEN_EXOIRED" in text
        or "FAIL_SYS_TOKEN_EXPIRED" in text
        or "令牌过期" in text
        or "令牌为空" in text
    )


def _item_id(obj: dict) -> str | None:
    value = obj.get("id")
    if value is not None and value != "":
        text = str(value).strip()
        if text and text != "null":
            return text
    detail = obj.get("detailParams")
    if isinstance(detail, dict):
        from_detail = detail.get("itemId")
        if from_detail is not None and str(from_detail).strip():
            return str(from_detail).strip()
    item_id = obj.get("itemId")
    if item_id is not None and str(item_id).strip():
        return str(item_id).strip()
    return None


def parse_item_ids(root: dict) -> list[str]:
    data = root.get("data") if isinstance(root, dict) else None
    if not isinstance(data, dict):
        return []
    seen: list[str] = []
    cards = data.get("cardList")
    if not isinstance(cards, list):
        return seen
    for node in cards:
        if not isinstance(node, dict) or not node:
            continue
        payload = node.get("cardData")
        if not isinstance(payload, dict):
            payload = node
        item = _item_id(payload)
        if item and item not in seen:
            seen.append(item)
    return seen


def parse_first_card_id(root: dict) -> str | None:
    ids = parse_item_ids(root)
    return ids[0] if ids else None
