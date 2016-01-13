# Typesafe Activator Documentation

> Activator can be used from a web browser or from a command line.

## Launch Instructions

Once installed you can run Typesafe Activator's UI either from a file browser or from a command line.  If you run the Activator UI from within an application's root directory that application will be automatically opened.  The `activator` command is a wrapper around [sbt](http://www.scala-sbt.org/) when used from an existing application's root directory.  When used outside of a application directory you can either create a new application by running `activator new` or launch the UI.

### Launch the UI on Windows

#### From Windows Explorer

1. Right-click on the `activator.bat` file
2. Select "Open"
3. If prompted with a warning, select to continue

#### From a Command prompt

1. Navigate to the directory containing the `activator.bat` file
2. Run: `activator ui`

### Launch the UI on Mac

#### From Finder

1. Ctrl-click or right-click on the `activator` file
2. Select "Open"
3. When prompted, select "Open"

#### From Terminal

1. Navigate the the directory containing the `activator` file
2. Run: `./activator ui`

### Launch the UI on Linux

#### From a GUI file browser

1. Double-click on the `activator` file

#### From Terminal

1. Navigate the the directory containing the `activator` file
2. Run: `./activator ui`

## The Activator UI

Once you have launched the UI it can be reached in your browser at: [http://localhost:8888]

### Creating New Applications

If you have launched the UI outside of a application's root directory you can now create a new application based on one of the templates.  Both Typesafe and the community have contributed templates to Activator.  [Learn more about how to contribute your own template](http://typesafe.com/activator/template/contribute)

Once you have selected a template and optionally entered a name and location, select the *Create* button to have your new application created.


### Open an Existing Application Using the Web Interface

Existing applications can be opened by running `activator` or `activator.bat` from a project's root directory.  If the Activator UI is already running, then open [http://localhost:8888/home] in your browser and either select a known existing app, or click the *Find Existing* button to browse to an existing app.


### Working with Applications in the Activator UI

Once you have created or opened an application in the Activator UI you can:
* Read the Tutorial
* Browse & edit the code (select *Code*)
* Open the code in IntelliJ IDEA or Eclipse (select *Code* then the *Open* dropdown)
* See the compile output (select *Compile*)
* Test the application (select *Test*)
* Run the application (select *Run*)
* Inspect Actors and Requests in Play Framework and Akka applications  (select *Inspect*)
* Create a new application or open an existing one (select the application's name in the top right, then select *Manage Applications*)

Whenever you save changes to a source file in the application, the changes will be recompiled, the application will be re-run, and the tests will be re-run.


### Re-Launching Activator

To make it easy to re-launch Activator (either from a file browser or command line) after you've shut it down, you can either launch it the same way you did the first time, or you can launch it from the new application's root directory.  If you launch it from the application's root directory then the UI will automatically launch with the application open.  Otherwise you will need to open the existing application.  To make it easy for others to launch Activator for your application you can put the `activator`, `activator.bat`, and `activator-launch-VERSION.jar` files in your SCM.


## Activator Configuration

### Proxy Configuration

When running activator behind a proxy, some additional configuration is needed.  Open the `~/.activator/activatorconfig.txt` file in a text editor (creating it if necessary.  Once you have the file open and ready to configure, we need to make sure it has the following lines:

    # This are the proxy settings we use for activator
    # Multiple proxy hosts can be used by separating them with a '|' sign
    # Do not enclose the proxy host(s) in quotes
    -Dhttp.proxyHost=PUT YOUR PROXY HOST HERE
    -Dhttp.proxyPort=PUT YOUR PROXY PORT HERE
    # Here we configure the hosts which should not go through the proxy.  You should include your private network, if applicable.
    -Dhttp.nonProxyHosts="localhost|127.0.0.1"
    # These are commented out, but if you need to use authentication for your proxy, please fill these out.
    #-Dhttp.proxyUser=PUT YOUR PROXY USER HERE
    #-Dhttp.proxyPassword=PUT YOUR PROXY PASSWORD HERE

### Increasing the Network Timeout
                        
By default Activator has a 10 second timeout to fetch the latest templates and check for a new version.  On slow internet connections it might be necessary to increase that timeout.  You can do that from the command line by including a `-Dactivator.timeout=30s` parameter or by putting that parameter into your `~/.activator/activatorconfig.txt` file.

## Activator Resources
[Source Code](https://github.com/typesafehub/activator)
[Issue Tracker](https://github.com/typesafehub/activator/issues)

## License

Typesafe Activator is licensed under [Apache License, Version 2.0](http://opensource.org/licenses/Apache-2.0); including the command-line Launcher, HTML & JavaScript UI, Play Framework based server, and all tests included in the project. The templates available in Typesafe Activator are licensed individually by the owner.  Refer to template source code or owner for license information.