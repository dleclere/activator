/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
require.config({
  baseUrl:  '/public',
  // hack for now due to problem loading plugin loaders from a plugin loader
  map: {
    '*': {
      'css': '../../webjars/require-css/0.0.7/css',
      'text': '../../webjars/requirejs-text/2.0.10/text'
    }
  },
  paths: {
    commons:  'commons',
    test:     'test',
    plugins:  'plugins',
    services: 'services',
    widgets:  'widgets'
  }
});

require([
  // Vendors
  'lib/requirejs-text/text',
  'lib/require-css/css',
  'lib/jquery/jquery',
  'lib/knockout/knockout',
  'lib/keymage/keymage',
  // other
  'commons/utils'
],function(v1,v2,v3,v4,v5,v6, utils){

  function assert(t) {
    if (!t)
      throw new Error("fail");
  }

  var failedCount = 0;
  var passedCount = 0;
  var results = [];

  function test(desc, body) {
    var failed = null;
    try {
      body();
    } catch (e) {
      failed = e;
    }
    var r;
    if (failed === null) {
      r = { desc: desc, passed: true };
      passedCount += 1;
    } else {
      r = { desc: desc, passed: false, error: failed };
      failedCount += 1;
    }
    results.push(r);
    var node = $('.results')[0], elem;
    if (r.passed) {
      elem = $('<div class="passed"></div>').text(r.desc + ": OK");
      $(node).append(elem);
    } else {
      elem = $('<div class="failed"></div>').text(r.desc + ": FAILED: " + r.error)
      $(node).append(elem);
    }

    $('.summary').text("" + passedCount + " passed, " + failedCount + " failed");
  }

  function afterTests() {
    var overall;
    if (failedCount === 0)
      overall = "All tests passed!";
    else
      overall = "YOU HAVE FAILED";
    $('.summary').text($('.summary').text() + "  " + overall);
  }

  /* TESTS FOLLOW */

  test("Class utilities", function() {
    var Class = utils.Class;

    var B = Class({
      init: function(a,b) {
        this.a = a;
        this.b = b;
        this.name = "B";
        this.count = 1;
      },
      foo: function() {
        return "B";
      }
    });

    var S1 = Class(B, {
      init: function(a, b) {
        this.a1 = a;
        this.b1 = b;
        this.name = "S1";
        this.count += 1;
      },
      classInit: function(proto) {
        proto.classValue = 42;
      },
      foo: function() {
        return "S1";
      }
    });

    var S2 = Class(S1, {
      init: function(a, b) {
        this.a2 = a;
        this.b2 = b;
        this.name = "S2";
        this.count += 1;
      },
      classInit: function(proto) {
        proto.classValue = 43;
      },
      foo: function() {
        return "S2";
      }
    });

    var b = new B(1,2);
    var s1 = new S1(3,4);
    var s2 = new S2(5,6);

    assert(b.name === "B");
    assert(b.a === 1);
    assert(b.b === 2);
    assert(b.foo() === "B");
    assert(b.count === 1);

    assert(s1.name === "S1");
    assert(s1.a1 === 3);
    assert(s1.b1 === 4);
    assert(s1.foo() === "S1");
    assert(s1.count === 2);
    assert(s1.classValue === 42);

    assert(s2.name === "S2");
    assert(s2.a2 === 5);
    assert(s2.b2 === 6);
    assert(s2.foo() === "S2");
    assert(s2.count === 3);
    assert(s2.classValue === 43);
  });

  /* END OF TESTS */
  afterTests();

});
