/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(function() {

  // -------------
  // Type Checking
  // -------------
  // Check types (or equality) for passed values and objects:
  // EXAMPLES =>
  // - Strings:
  //    is(String, "foo")
  //    is("foo", "foo")
  //    is(/foo/, "foo")
  // - Numbers:
  //    is(Number, 42)
  //    is(42, 42)
  // - Boolean
  //    is(Boolean, true)
  //    is(true, true)
  // - Date
  //    is(Date, new Date(1999,1,1))
  // - Array
  //    is(Array, ["foo"])
  //    is([], ["foo"])
  //    is([String], ["foo"])
  //    is(["foo"], ["foo"])
  // - Objects
  //    is({ name: String, emails: [/mailRegex/] }, ...)
  function is(type, value) {
    var typeoftype  = typeof type;
    var typeofvalue = typeof value;
    if      (type === null)    return value !== undefined;
    else if (typeoftype === "string")   return type === value;
    else if (typeoftype === "number")   return type === value;
    else if (typeoftype === "boolean")  return type === value;
    else if (typeoftype === "function") return (typeofvalue === typeof type() || value instanceof type);
    else if (type instanceof Array) {
      if (!(value instanceof Array)) return false;
      if (type.length === 1 && value.length >= 0) {
        for (var i in value){
          if (!is(type[0], value[i])) return false;
        }
      }
      return true;
    }
    else if (type instanceof RegExp) {
      if (typeoftype === "string") return false;
      return type.test(value);
    }
    else if (typeoftype === "object") {
      if (!value) return false;
      for (var j in type){
        if (!is(type[j], value[j])) return false;
      }
      return true;
    }
  }

  return {
    check: is,
    curry: function(pattern, value) {
      if (!pattern)
        return true;
      else if (value !== undefined)
        return is(pattern, value);
      else
        return function(value) {
          return is(pattern, value);
        }
    }
  }

})
