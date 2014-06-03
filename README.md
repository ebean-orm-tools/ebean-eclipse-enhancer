avaje-ebeanorm-eclipse
======================

ebean orm eclipse plugin for auto enhancement using the 4.x.x java agent - you **must** use this version of the plugin if you are using ebean 4.x.x.

### important

this plugin is incompatible with the existing ebean plugin. please uninstall the original plugin before continuing. don't forget to disable the existing nature first! failure to do so will require manual editting of the .project files to remove existing entries.

if you have already performed the above step to install the prior version of this plugin supporting 3.x.x agent, you may perform a direct upgrade.

### build

`mvn -Pdist-plugin install site`

this will create an update site (and cooresponding zip for local install) in `update.site/target`

you must perform a build before importing into eclipse to properly setup dependencies

### eclipse import

if you are using m2eclipse, you can just use the 'import existing maven projects' feature to get up and running.

if you aren't, you're on you're own. :)
