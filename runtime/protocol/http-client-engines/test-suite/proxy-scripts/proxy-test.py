# Simple mitmproxy script to intercept a request and respond directly without hitting the real host
# This is used to verify proxy configuration on HttpClientEngineConfig
# see: https://docs.mitmproxy.org/stable/addons-examples/#http-reply-from-proxy
from mitmproxy import http


def request(flow):
    if flow.request.pretty_url.strip("/") == "http://aws.amazon.com":
        flow.response = http.Response.make(
            200,
            b"hello proxy",
            {"Content-Type": "text/html"}
        )
