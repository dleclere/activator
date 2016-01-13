/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'widgets/help/shortcuts',
  './router',
  'services/sbt'
], function(
  shortcuts,
  router,
  sbt
) {

  var isMac = navigator.platform.toUpperCase().indexOf('MAC')>=0;
  var activeKeyboard = true;

  var keyCodes = {
    13:  'ENTER',
    27:  'ESC',
    37:  'LEFT',
    39:  'RIGHT',
    38:  'TOP',
    40:  'BOTTOM',
    82:  'R',
    83:  'S',
    84:  'T',
    86:  'V',
    87:  'W',
    219: '[',
    221: ']',
    191: '/',
    49:  '1',
    50:  '2',
    51:  '3',
    52:  '4',
    53:  '5',
    54:  '6',
    55:  '7'
  }

  function modifierKey(e){
    return ((isMac && e.metaKey) || e.ctrlKey)
  }

  function notEditing(e){
    // if we're editing something we cancel
    return !(!activeKeyboard || e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA');
  }

  // Careful, order matters here
  $(document)
    .keydown(function(e){
      // ESCAPE blurs
      if(e.keyCode === 27) {
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
          e.target.blur();
          $(document.body).scrollReveal();
          return false;
        } else {
          $(".dropdown.opened").removeClass("opened");
        }
      // CTRL + D
      } else if (e.keyCode === 68 && e.ctrlKey) {
        $("#appStatus .onoff").trigger("click");
      }
    }).keydown(function(e){
      var isMeta = modifierKey(e);
      // Check if key is registered in current plugin, or in main view
      if ((notEditing(e) || isMeta) && router.current().keyboard){
        return router.current().keyboard(keyCodes[e.keyCode], isMeta, e);
      }
    }).keydown(function(e){
      // ALL shortcuts
      var key = keyCodes[e.keyCode];
      if (notEditing(e)){
        if (key === "T"){
          e.preventDefault();
          e.stopPropagation();
          $("#omnisearch input").focus();
        } else if (key === "/"){
          e.preventDefault();
          e.stopPropagation();
          shortcuts();
        }
      }
    });

  return {
    activeKeyboard: activeKeyboard
  }

});
