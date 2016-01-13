/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'main/router',
  'services/sbt/app'
],function(
  router,
  app
) {

  var metaData =      ko.observable(),
      pages =         ko.observableArray([]),
      table =         ko.observableArray([]),
      hasTutorial =   ko.observable(false),
      page =          ko.observable(null),
      index =         ko.observable(null);

    // NOTE:
    // Enclosing the state in the service, to use it in both Plugin and Panel

  var gotoPage = function(id){
    if(router.current().id === "tutorial"){
      window.location.hash = "#tutorial/"+id;
    } else {
      id = parseInt(id);
      var p = pages()[id];
      page(p);
      index(id);
    }
  }
  var gotoPrevPage = function(){
    if(!noPrevPage()) gotoPage(index()-1);
  }
  var gotoNextPage = function(){
    if(!noNextPage()) gotoPage(index()!==null?index()+1:0);
  }
  var noPrevPage  = ko.computed(function(){
    return index() === 0;
  });
  var noNextPage  = ko.computed(function(){
    return index() === table().length-1;
  });

  var retrieveMetadata = function() {
    if (serverAppModel.template) {
      $.getJSON("/api/templates/" + serverAppModel.template + "/meta", function(data){
        metaData(data);
      });
    } else {
      debug && console.log("Missing mandatory serverAppModel.template information to retrieve metadata for the template project.");
    }
  };

  retrieveMetadata();

  function loadTutorial(){
    $.get("tutorial/index.html", function(data){
      hasTutorial(true);
      // parseHTML dumps the <html> <head> and <body> tags
      // so we'll get a list with <title> some <div> and some text nodes
      var htmlNodes = $.parseHTML(data),
          _pages = [],
          _table = [];
      $(htmlNodes).filter("div").each(function(i,el){
        $("button[data-exec]", el).each(function() {
          var title = $(this).text();
          var command = $(this).attr('data-exec');
          if (command === 'run' || command === 'compile' || command === 'start') return;
          if (!app.customCommands().filter(function(i) { return i.command === command }).length){
            app.customCommands.push({
              command: command,
              title: title
            })
          }
        });
        $("a", el).each(function(j, link) {
          // Open external links in new window.
          if (link.getAttribute('href').indexOf("http://") === 0 && !link.target){
            link.target = "_blank";
          // Force shortcut class on links to code
          } else if (link.getAttribute('href').indexOf("#code/") === 0){
            $(link).addClass("shortcut");
          }
        });
        var title = $("h2", el).remove().html() || $(el).text().substring(0,40) + "...";
        _pages.push({ index: i, title: title, page: el.innerHTML });
        _table.push(title);
      });
      pages(_pages);
      table(_table);
      console.log(index())
      if (index()){
        window.location.hash = "#tutorial/";
        gotoPage(index());
      }
    });
  }
  loadTutorial();

  return {
    hasTutorial:  hasTutorial,
    loadTutorial: loadTutorial,
    isLocal:      window.serverAppModel.hasLocalTutorial,
    metaData:     metaData,
    table:        table,
    pages:        pages,
    page:         page,
    index:        index,
    gotoPage:     gotoPage,
    gotoPrevPage: gotoPrevPage,
    gotoNextPage: gotoNextPage,
    noPrevPage:   noPrevPage,
    noNextPage:   noNextPage
  }

});
