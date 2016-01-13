define(function() {

  var Format = (function() {
    function Format() {}

    Format.prototype.shortenNumber = function(n, decimals) {
      if (decimals == null) {
        decimals = 1;
      }
      if (n < 1000) {
        return n;
      }
      if (n < 1000000) {
        return parseFloat((n / 1000).toFixed(decimals)) + 'k';
      }
      return parseFloat((n / 1000000).toFixed(decimals)) + 'M';
    };

    Format.prototype.cut = function(num, dig) {
      var shorty;
      shorty = ((num !== void 0 ? num : "N/A") + "").slice(0, dig || num.length);
      if (shorty.indexOf(".") === shorty.length - 1) {
        shorty = shorty.slice(0, -1);
      }
      return shorty;
    };

    Format.prototype.shorten = function(num) {
      return this.cut(num, 5);
    };

    Format.prototype.mini = function(num) {
      return this.cut(num, 3);
    };

    Format.prototype.units = function(unit, val, formatter) {
      var apply, parsedVal, shorten, wasBad, wordSpace;
      if (unit == null) {
        unit = "";
      }
      wasBad = val === void 0 || val === null;
      if (wasBad) {
        val = 0;
      }
      apply = function(uvArray) {
        if (wasBad) {
          uvArray = ["", "N/A"];
        }
        if (formatter) {
          return formatter(uvArray[0], uvArray[1]);
        } else {
          return uvArray[1] + wordSpace + uvArray[0];
        }
      };
      parsedVal = parseFloat(val, 10);
      if (isNaN(parsedVal)) {
        wasBad = true;
      }
      wordSpace = unit === "%" ? "" : " ";
      shorten = function(u, v) {
        var micro;
        switch (u) {
          case "messages/second":
            return ["msg/s", v];
          case "messages/millisecond":
            return ["msg/ms", v];
          case "minutes":
            return ["mins", v];
          case "seconds":
            if (v > 60 * 10) {
              return shorten("minutes", v / 60 * 10);
            } else {
              return ["s", v];
            }
            break;
          case "milliseconds":
            if (v > 10000) {
              return shorten("seconds", v / 1000);
            } else {
              return ["ms", v];
            }
            break;
          case "microseconds":
            if (v > 10000) {
              return shorten("milliseconds", v / 1000);
            } else {
              micro = String.fromCharCode(0xB5);
              return [micro + 's', v];
            }
            break;
          case "bytes":
            if (v > 10000) {
              return shorten("kilobytes", v / 1024);
            } else {
              return ["B", v];
            }
            break;
          case "kilobytes":
            if (v > 10000) {
              return shorten("megabytes", v / 1024);
            } else {
              return ["kB", v];
            }
            break;
          case "megabytes":
            return ["MB", v];
          case "bytes/second":
            if (v > 10000) {
              return shorten("kilobytes/second", v / 1024);
            } else {
              return ["B/s", v];
            }
            break;
          case "kilobytes/second":
            if (v > 10000) {
              return shorten("megabytes/second", v / 1024);
            } else {
              return ["kB/s", v];
            }
            break;
          case "megabytes/second":
            return ["MB/s", v];
          default:
            return [u, v];
        }
      };
      return apply(shorten(unit, parsedVal));
    };

    Format.prototype.humanReadableDuration = function(v, unit, maxLength) {
      var a, addPart, result;
      if (maxLength == null) {
        maxLength = 16;
      }
      result = "";
      a = (function() {
        switch (unit) {
          case "milliseconds":
            return v;
          case "microseconds":
            return Math.floor(v / 1000);
          case "seconds":
            return v * 1000;
          default:
            return 0;
        }
      })();
      addPart = function(b, unit) {
        if (a >= b) {
          if (result !== "") {
            result += " ";
          }
          result += Math.floor(a / b) + " " + unit;
          return a = a % b;
        }
      };
      addPart(86400000, "d");
      if (result.length + 4 <= maxLength) {
        addPart(3600000, "h");
        if (result.length + 7 <= maxLength) {
          addPart(60000, "min");
          if (result.length + 4 <= maxLength) {
            addPart(1000, "s");
          }
        }
      }
      if (result === "") {
        result = "" + a + " ms";
      }
      return result;
    };

    Format.prototype.formatTime = function(date, seconds, milliseconds, UTC) {
      var part, result;
      if (seconds == null) {
        seconds = true;
      }
      if (milliseconds == null) {
        milliseconds = true;
      }
      if (UTC == null) {
        UTC = false;
      }
      if (date == null) {
        return "";
      }
      if (date.getUTCFullYear == null) {
        date = new Date(date);
      }
      part = function(x, numberOfDigits) {
        var str;
        if (numberOfDigits == null) {
          numberOfDigits = 2;
        }
        str = "" + x;
        if (numberOfDigits >= 3 && x < 100) {
          str = "0" + str;
        }
        if (numberOfDigits >= 2 && x < 10) {
          str = "0" + str;
        }
        return str;
      };
      result = "";
      result += part(UTC ? date.getUTCHours() : date.getHours());
      result += ":";
      result += part(UTC ? date.getUTCMinutes() : date.getMinutes());
      if (seconds) {
        result += ":";
        result += part(UTC ? date.getUTCSeconds() : date.getSeconds());
      }
      if (seconds && milliseconds) {
        result += ":";
        result += part((UTC ? date.getUTCMilliseconds() : date.getMilliseconds()), 3);
      }
      return result;
    };

    Format.prototype.formatDate = function(date, UTC) {
      var part, result;
      if (UTC == null) {
        UTC = true;
      }
      if (date == null) {
        return "";
      }
      if (date.getUTCFullYear == null) {
        date = new Date(date);
      }
      part = function(x) {
        var str;
        str = "" + x;
        if (x < 10) {
          str = "0" + str;
        }
        return str;
      };
      result = "";
      result += part(UTC ? date.getUTCFullYear() : date.getFullYear());
      result += "-";
      result += part(UTC ? date.getUTCMonth() + 1 : date.getMonth() + 1);
      result += "-";
      result += part(UTC ? date.getUTCDate() : date.getDate());
      return result;
    };

    Format.prototype.formatTimestamp = function(date, UTC) {
      if (UTC == null) {
        UTC = false;
      }
      if (date == null) {
        return "";
      }
      if (date.getUTCFullYear == null) {
        date = new Date(date);
      }
      return this.formatDate(date, UTC) + " " + this.formatTime(date, true, true, UTC);
    };

    Format.prototype.nanosToMillis = function(nanos, decimals) {
      return (nanos / 1000000).toFixed(decimals);
    };

    Format.prototype.markdownLinks = function(str){
      // Search for Html tags, indicating that the text was not escaped.
      // Should never be reached, but in case we forgot to escape before using that formatter...
      try {
        if (str.match(/.*<.*>.*/)) {
          throw "Unauthorized chars, we are preventing XSS.";
        }
      } catch (e) {
        return "Can't display text.";
      }

      // md links
      str = str.replace(/\[([^\].]+)\]\((https?\:\/\/[a-z0-9\-]+\.[.^\S^\)]+)\)/g, '<a href="$2" target="_blank">$1</a>')

      return str;
    }

    return Format;

  })();

  return new Format();

});
