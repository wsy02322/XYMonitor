import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from inspector import compare, fail
from mtop import is_success, is_token_error, md5, parse_first_card_id, parse_item_ids, request_data, sign, APP_KEY
from monitor import Monitor


class MtopTest(unittest.TestCase):
    def test_sign_matches_classic_h5_md5(self):
        token = "0566e35c2205a2d9bf06eb19a0db7e95"
        t = "1787912345678"
        data = '{"needGroupInfo":true,"pageNumber":1,"userId":"1666703902","pageSize":20}'
        expected = md5(f"{token}&{t}&{APP_KEY}&{data}")
        self.assertEqual(expected, sign(t, token, data))
        self.assertEqual(32, len(expected))

    def test_parse_first_page_ids(self):
        raw = """
            {
              "api":"mtop.idle.web.xyh.item.list",
              "ret":["SUCCESS::调用成功"],
              "data":{
                "topItem":{"cardData":{"id":"999","title":"top"}},
                "cardList":[
                  {"cardData":{"id":1031066924442,"title":"a","detailParams":{"itemId":"1031066924442"}}},
                  {"cardData":{"id":"1029988109330","detailParams":{"itemId":"1029988109330"}}},
                  {"cardData":{"detailParams":{"itemId":"888"}}}
                ]
              }
            }
        """
        root = json.loads(raw)
        self.assertEqual(["1031066924442", "1029988109330", "888"], parse_item_ids(root))
        self.assertEqual("1031066924442", parse_first_card_id(root))

    def test_empty_top_item_is_ignored(self):
        root = json.loads('{"ret":["SUCCESS::调用成功"],"data":{"topItem":{},"cardList":[{"cardData":{"id":"1"}}]}}')
        self.assertEqual(["1"], parse_item_ids(root))

    def test_token_and_success_flags(self):
        empty = json.loads('{"ret":["FAIL_SYS_TOKEN_EMPTY::令牌为空"],"data":{}}')
        ok = json.loads('{"ret":["SUCCESS::调用成功"],"data":{}}')
        fail_json = json.loads('{"ret":["FAIL_SYS_ILLEGAL_ACCESS::非法请求"],"data":{}}')
        self.assertTrue(is_token_error(empty))
        self.assertFalse(is_success(empty))
        self.assertTrue(is_success(ok))
        self.assertFalse(is_token_error(ok))
        self.assertFalse(is_success(fail_json))
        self.assertFalse(is_token_error(fail_json))

    def test_compact_request_data(self):
        self.assertEqual(
            '{"needGroupInfo":true,"pageNumber":1,"userId":"1666703902","pageSize":20}',
            request_data("1666703902"),
        )


class InspectorTest(unittest.TestCase):
    def test_first_success_is_baseline(self):
        result = compare("", "111")
        self.assertTrue(result.ok)
        self.assertTrue(result.baseline)
        self.assertFalse(result.changed)

    def test_first_id_change_alerts(self):
        result = compare("111", "222")
        self.assertTrue(result.changed)
        self.assertEqual("222", result.first_id)

    def test_same_id_does_not_alert(self):
        result = compare("111", "111")
        self.assertFalse(result.changed)

    def test_empty_page_fails(self):
        result = compare("111", "")
        self.assertFalse(result.ok)
        self.assertEqual("第一页没有商品", result.error)

    def test_fail_is_not_a_change(self):
        result = fail("timeout")
        self.assertFalse(result.changed)
        self.assertEqual("timeout", result.error)


class MonitorTest(unittest.TestCase):
    def setUp(self):
        fd, name = tempfile.mkstemp(suffix=".json")
        os.close(fd)
        self.state = Path(name)
        self.addCleanup(lambda: self.state.exists() and self.state.unlink())
        self.items = ["111"]

        def fetch(_user_id: str) -> str:
            return self.items[-1]

        self.notes: list[str] = []
        self.monitor = Monitor(
            state_path=str(self.state),
            fetch_first_id=fetch,
            notify=lambda text: self.notes.append(text) or True,
        )

    def test_baseline_then_change_sets_pending_once(self):
        self.monitor.user_id = "1666703902"
        snap = self.monitor.inspect_once_for_test()
        self.assertTrue(snap["baseline"])
        self.assertEqual("111", snap["firstId"])
        self.assertFalse(snap["pendingAlert"])
        self.items.append("222")
        snap = self.monitor.inspect_once_for_test()
        self.assertTrue(snap["pendingAlert"])
        self.assertEqual("222", snap["itemId"])
        self.assertEqual(["闲鱼上新 222"], self.notes)
        self.monitor.ack(item_id="222")
        snap = self.monitor.snapshot()
        self.assertFalse(snap["pendingAlert"])
        snap = self.monitor.inspect_once_for_test()
        self.assertFalse(snap["pendingAlert"])
        self.assertEqual(1, len(self.notes))

    def test_persists_first_id(self):
        self.monitor.user_id = "1666703902"
        self.monitor.inspect_once_for_test()
        other = Monitor(state_path=str(self.state), fetch_first_id=lambda _: "111")
        self.assertEqual("111", other.snapshot()["firstId"])


if __name__ == "__main__":
    unittest.main()
