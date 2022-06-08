"""
Addon fakes responses from an upstream origin for use in unit tests. 

usage: mitmdump -s fakeupstream.py --set fakeupstream=aws.amazon.com

see https://docs.mitmproxy.org/stable/api/events.html
"""
from mitmproxy import http, ctx
from typing import Optional


class FakeUpstreamResponse:
    """
    Generate a fake upstream server response for a given host
    """

    def load(self, loader):
        loader.add_option(
            name="fakeupstream",
            typespec=Optional[str],
            default=None,
            help="Set upstream host to fake response for"
        )

    def request(self, flow):
        if flow.response != None:
            # some other addon has responded already (e.g. proxyauth)
            return

        fakeupstream = ctx.options.fakeupstream
        if fakeupstream is None:
            return

        if flow.request.host == fakeupstream:
            print(f"faking response for {fakeupstream}")
            flow.response = http.Response.make(
                200,
                b"hello proxy",
                {"Content-Type": "text/html"}
            )


addons = [
    FakeUpstreamResponse()
]
