
```
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>

<!-- Put site-specific property overrides in this file. -->

<configuration>

 <property>
    <name>mapred.system.dir</name>
    <value>/grid/0/dev/vborkar/hadoop-rack345-1-mrsys</value>
 </property>

 <property>
    <name>mapred.local.dir</name>
    <value>/grid/0/dev/vborkar/hadoop-rack345-1-tmp1,/grid/1/dev/vborkar/hadoop-rack345-1-tmp1,/grid/2/dev/vborkar/hadoop-rack345-1-tmp1,/grid/3/dev/vborkar/hadoop-rack345-1-tmp1</value>
 </property>

 <property>
    <name>mapred.job.tracker</name>
    <value>xyz.com:29007</value>
 </property>
 <property>
    <name>mapred.tasktracker.map.tasks.maximum</name>
    <value>4</value>
 </property>
  <property>
     <name>mapred.tasktracker.reduce.tasks.maximum</name>
     <value>4</value>
  </property>
  <property>
     <name>mapred.job.tracker.info.port</name>
     <value>47040</value>
  </property>
  <property>
     <name>mapred.task.tracker.output.port</name>
     <value>47041</value>
  </property>
  <property>
    <name>mapred.task.tracker.report.port</name>
    <value>47042</value>
  </property>
  <property>
    <name>mapred.job.tracker.info.bindAddress</name>
    <value>10.145.12.136</value>
  </property>
  <property>
    <name>tasktracker.http.port</name>
    <value>40069</value>
  </property>
  <property>
     <name>mapred.job.reuse.jvm.num.tasks</name>
     <value>2</value>
  </property>
  <property>
       <name>mapred.min.split.size</name>
     <value>268435456</value>
  </property>
  <property>
     <name>mapred.map.tasks.speculative.execution</name>
     <value>false</value>
  </property>
  <property>
     <name>mapred.reduce.tasks.speculative.execution</name>
     <value>false</value>
  </property>
  <property>
     <name>mapred.map.child.java.opts</name>
     <value>-Xmx2048M</value>
  </property>
  <property>
    <name>mapred.reduce.child.java.opts</name>
    <value>-Xmx2048M</value>
 </property>
</configuration>
```