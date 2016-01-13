/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "services/ajax",
  "services/sbt",
  'ace/ace'
],function(
  fs,
  sbt,
  ace
){

  var documentState = function(doc){
    var self = this;
    self.isText = true;
    self.title = doc.title;
    self.location = doc.location;
    self.active = ko.observable(false); // == displayed document
    self.body = ko.observable("");
    self.lineNumber = doc.lineNumber;

    // Indicates a network activity on the file
    self.working = ko.observable(0);

    // Used for hilighting
    self.mode = highlightModeFor(self.location);
    // Ace document & session
    self.session = new ace.EditSession(self.body(), 'ace/mode/'+self.mode);
     // Ace undo history
    self.session.setUndoManager(new ace.UndoManager());

    // Sync Ace content with local value
    self.edited = ko.observable(false);
    // self.body.subscribe(function(value) {
    //   // TODO: check if document is edited, then ask for confirmation
    //   self.session.setValue(value);
    // });
    self.session.on("change", function(e){
      self.edited(self.session.getValue() !== self.body());
    });

    // Annotation (error, warning...)
    self.showAnnotations = function(_) {
      var annotations = _.filter(function(m) {
        return m.position.sourcePath === self.location;
      }).map(function(m) {
        // Translate sbt error kinds, to ace annotations types
        var aceLevel = m.severity === 'Error' ? 'error': m.kind === 'Warn' ? 'warning': 'info';
        return {
          row: m.position.line - 1, // Ace count from zero
          column: m.position.offset,
          text: m.message,
          type: aceLevel
        }
      });
      self.session.clearAnnotations();
      self.session.setAnnotations(annotations);
    }
    self.showAnnotations(sbt.tasks.compilationErrors());

    // Save document
    self.save = function(callback){
      self.working(self.working()+1);
      var content = self.session.getValue();
      fs.save(self.location, content).success(function() {
        self.body(content);
        self.edited(false);
        self.working(self.working()-1);
      });
    }

    // Get saved version
    self.revert = function(){
      self.working(self.working()+1);
      fs.show(self.location).then(function(content) {
        self.body(content);
        self.edited(false);
        self.session.setValue(content);
        self.working(self.working()-1);
      });
    }
    self.revert(); // Getting the server version right away

    // Right click on the tab
    self.contextmenu = {
      'Save':     self.save.bind(self),
      'Revert':   self.revert.bind(self)
    }
  }

  function highlightModeFor(filename) {
    var ext = filename.split('.').pop().toLowerCase();
    if (ext === "scala" || ext === "sbt") return "scala";
    if (ext === "java") return "java";
    if (ext === "js") return "javascript";
    if (ext === "html") return "html";
    if (ext === "css") return "css";
    if (ext === "json") return "json";
    if (ext === "xml") return "xml";
    if (ext === "clj") return "clojure";
    if (ext === "dart") return "dart";
    if (ext === "erl") return "erlang";
    if (ext === "groovy") return "groovy";
    if (ext === "haml") return "haml";
    if (ext === "hs") return "haskell";
    if (ext === "latex") return "latex";
    if (ext === "less") return "less";
    if (ext === "ls") return "livescript";
    if (ext === "md") return "markdown";
    if (ext === "py") return "python";
    if (ext === "rb") return "ruby";
    if (ext === "rs") return "rust";
    if (ext === "sass") return "sass";
    if (ext === "scss") return "scss";
    if (ext === "sql") return "sql";
    if (ext === "styl") return "stylus";
    if (ext === "svg") return "svg";
    if (ext === "textile") return "textile";
    if (ext === "ts") return "typescript";
    if (ext === "yaml") return "yaml";
    return "text";
  }

  return documentState;

})
