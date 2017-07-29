![alt text](http://davidruescas.com/wp-content/uploads/2017/05/Untitled.png)

# pViz

pViz provides a visualization of the [nMix](https://github.com/nVotesOrg/nMix) Crypto Protocol as it is executing. It's made up of an akka-http server plus a ScalaJS webclient.

# Running

Currently pViz must run on an nMix trustee, such that there is a working copy of the bulletin board available. This working copy is used as the data source to visualize the protocol. All that needs configuring is the filesystem location of that working copy. To configure this, edit the build.sbt file and fine the line

```
javaOptions += "-Dpviz.target=../nMix/demo/datastore/repo"
```

This is configured to work by default if you have cloned the pViz project from the same root folder as where you cloned nMix. So the path would to nMix would be '../nMix'. If not, you must change this value.

To start, run

```
sbt ~re-start
```

And go to `localhost:8080`

# Acknowledgements

pViz used the skeleton [workbench example app](https://github.com/lihaoyi/workbench-example-app) by [lihaoyi](https://github.com/lihaoyi/) as a starting point.