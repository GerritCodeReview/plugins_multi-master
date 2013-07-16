<!---
Copyright (c) 2013, The Linux Foundation. All rights reserved.
-->

Multi-Master Configuration
==========================

Multiple master instances of Gerrit can be set up to mitigate server load by
allowing users to access a server which has more free resources. In the future,
users may be redirected based on server resources.

The multiple master arrangement consists of multiple servers running on
different host:port combinations, that share a common back-end (using an NFS).
In this arrangement, no automatic load balancing or fail over is available.
Users can manually switch between the servers to manually load balance.

Enabling Multi-Master
---------------------

All servers must use the same database to ensure all users have access to the
same data. PostgreSQL or MySQL should be used as the database - the default,
embedded H2, cannot be used as multiple servers cannot connect to the same H2
database in embedded mode. Setup instructions for PostgreSQL 8.4 can be found
[here](#postgresql).

Initialize the Gerrit instances as shown below:

```
  # NOTE: MySQL or PostgreSQL must be set up (see PostgreSQL setup instructions
    at the bottom of this document)
  $ java -jar {path/}gerrit.war init -d <site1>
  # choose any location on the NFS for git repository location. NOTE: the
    location of the git repository must be shared by all servers. If you use the
    default, [git], then the repository will be located on this server in
    'site1/git'. It is recommended that you avoid the default and specify the
    full path to the git repository, even if it is on this server. The full path
    can then be used when setting up the other servers.
  # choose anything except "h2" server type for database
  # choose database options, username, and password based on your setup
  # you can choose the default options for the rest
```

```
  $ java -jar {path/}gerrit.war init -d <site2>
  # choose the same git repository location that the first instance uses
  # choose same options as for the first instance for sql database, since we
    want all instances to use the same database
  # choose any unused port >= 1024 for ssh daemon so we can have users use
    different ports to load balance
  # choose any unused port >= 1024 for http daemon
  # you can choose the default options for the rest
```

Gerrit uses caches to speed up data access. Two caches of note are:

* accounts: contains important details of an active user, including their
display name, preferences, known email addresses, and group memberships
* projects: contains project description records

Each server would use its own cache. Thus, changes to account information and
new projects created on one server would not show up on the other server(s). In
some cases, such as group membership, there would be a large delay before
the data will show up on the other servers.
To avoid these issues the 'accounts' and 'projects' caches need to be disabled
so that each server will get the up-to-date information from the database. Add
the following lines to `<site1>/etc/gerrit.config` and
`<site2>/etc/gerrit.config` to disable the caches:

```
  [cache "accounts"]
    memoryLimit = 0
    diskLimit = 0
  [cache "projects"]
    memoryLimit = 0
    diskLimit = 0
```

Restart all servers for the config changes to take effect.

```
  $ ./<site1>/bin/gerrit.sh restart
  $ ./<site2>/bin/gerrit.sh restart
```

All servers can now be used to get the same data. Connect to the different
servers using the ports chosen during initialization.

Issues
------

* Each server operates slower as a result of the disabled caches
* Each server has different web session information, so logging out of one
server does not log the user out of the other servers

<a name="postgresql">
PostgreSQL Setup
----------------
</a>

Install PostgreSQL, create a user for the web application within Postgres,
assign it a password, create a database to store the metadata, and grant the
user full rights on the newly created database.

```
  $ sudo apt-get install postgresql
  $ sudo -u postgres createuser -s <username>
  $ createuser -RDIElPS <db_username>    # you can choose gerrit2 as username
  $ createdb -E UTF-8 -T template0 --locale=en_US.utf8 -O <db_username> reviewdb
```
