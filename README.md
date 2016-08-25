ebean-eclipse
======================

Ebean ORM eclipse enhancement plugin.  This performs Ebean enhancement for both entity beans and "query beans" as part of Eclipse IDE compile.

### Update site

http://ebean-orm.github.io/eclipse/update

### Notes

You can use this plugin in conjunction with other enhancement like the maven plugin.

Note that when using the 8.x.x java agent - you **must** use Ebean 8.x.x

For older plugins to use with Ebean 4.x to Ebean 7.x use the update site at: http://ebean-orm.github.io/eclipse/update-4.11.2 or get a build from https://github.com/ebean-orm/ebean-eclipse-enhancer/tree/master/builds 


### Information

If you simply want to install this plugin into Eclipse, please follow the instructions here: http://ebean-orm.github.io/docs/setup/eclipse-apt

This project is the source code for the Eclipse plugin for Ebean ORM enhancement. You do not need to build this project if you simply want to install the plugin in Eclipse. Visit the above URL instead.

### Build from source

`mvn -Pdist-plugin install site`

This will create an update site (and cooresponding zip for local install) in `update.site/target`

You must perform a build before importing into eclipse to properly setup dependencies

### Pre-built archives

There are older versions of the plugin available as pre-built archives at:

https://github.com/ebean-orm/ebean-eclipse-enhancer/tree/master/builds 
