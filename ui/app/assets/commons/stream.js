define(['./types'], function(Types) {

  function noop() {}

  function EventStream() {
    this.callbacks = [];
  }

  // EventStream execution makes the actual runtime way faster
  function EventStreamExecution(callbacks) {
    return function(value) {
      if (callbacks.length > 1) {
        callbacks[0].call(noop, value, EventStreamExecution(callbacks.slice(1)));
      } else if (callbacks.length === 1) {
        callbacks[0].call(noop, value, noop);
      }
    };
  }

  EventStream.prototype.push = function(value) {
    EventStreamExecution(this.callbacks)(value);
    return this;
  };

  EventStream.prototype.clone = function(value) {
    var clone = new EventStream(this.callbacks.slice(0));
    if (this.onFail){
      clone.onFail = this.onFail;
    }
    return clone;
  };

  EventStream.prototype.fork = function() {
    var forked = new EventStream();
    var _call = function(value, next) {
      forked.push(value);
      next(value);
    };
    this.callbacks.push(_call);
    return forked;
  };

  // A mix between fork and filter,
  // Do no call next if it matches
  EventStream.prototype.match = function(process) {
    var forked = new EventStream();
    var _call = function(value, next) {
      if ( (typeof process === "function" && process(value)) || (typeof process === "object" && Types.check(process, value)) ){
        forked.push(value);
      } else {
        next(value);
      }
    }
    this.callbacks.push(_call);
    return forked;
  };

  // A faster match, that check strick equality for an attribute
  EventStream.prototype.matchOnAttribute = function(attribute, attributeValue) {
    var forked = new EventStream();
    var _call = function(value, next) {
      if (value !== undefined && value[attribute] === attributeValue){
        forked.push(value);
      } else {
        next(value);
      }
    }
    this.callbacks.push(_call);
    return forked;
  };

  EventStream.prototype.log = function(debug) {
    return this.each(function(e) {
      debug && console.debug(debug, e);
    });
  };

  // ----------------------------
  // Manage the FLOW of functions
  // ----------------------------
  // @asyncCall: function that takes the value and next function in parameters, such as:
  // mystream.filter(whatever).async(function(value, next){
  //   myAsyncCall(value, function(newValue){
  //     next(newValue)
  //   });
  // }).map(toSomething);
  EventStream.prototype.async = function(asyncCall) {
    this.callbacks.push(asyncCall);
    return this;
  };

  // -------------------------
  // Manage the FLOW of values
  // -------------------------
  EventStream.prototype.each = function(process) {
    var _call = function(value, next) {
      process(value);
      next(value);
    }
    this.callbacks.push(_call);
    return this;
  };

  EventStream.prototype.map = function(process) {
    var _call = function(value, next) {
      next(process(value));
    }
    this.callbacks.push(_call);
    return this;
  };

  EventStream.prototype.filter = function(process) {
    var _call = function(value, next) {
      if ( (typeof process === "function" && process(value)) || (typeof process === "object" && Types.check(process, value)) ) {
        next(value);
      }
    }
    this.callbacks.push(_call);
    return this;
  };

  EventStream.prototype.filterNot = function(process) {
    var _call = function(value, next) {
      if ( (typeof process === "function" && !process(value)) || (typeof process === "object" && !Types.check(process, value)) ) {
        next(value);
      }
    }
    this.callbacks.push(_call);
    return this;
  };

  EventStream.prototype.eachVal = function(process) {
    var _call = function(value, next) {
      value.forEach(process);
      next(value);
    }
    this.callbacks.push(_call);
    return this;
  };

  EventStream.prototype.mapVal = function(process) {
    var _call = function(value, next) {
      next(value.map(process));
    }
    this.callbacks.push(_call);
    return this;
  };

  EventStream.prototype.filterVal = function(process) {
    var _call = function(value, next) {
      next(value.filter(process));
    }
    this.callbacks.push(_call);
    return this;
  };

  EventStream.prototype.close = function() {
    this.callbacks = [];
  };

  return function() {
    return new EventStream()
  }

});
