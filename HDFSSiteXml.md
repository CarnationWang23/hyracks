
```
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>

<!-- Put site-specific property overrides in this file. -->

<configuration>

<property>
<name>dfs.datanode.port</name>
<value>57059</value>
</property>

<property>
 <name>dfs.datanode.ipc.address</name>
 <value>localhost:57020</value>
</property>

<property>
 <name>dfs.info.port</name>
 <value>57070</value>
</property>

<property>
 <name>dfs.secondary.info.port</name>
 <value>57071</value>
</property>

<property>
  <name>dfs.replication</name>
  <value>1</value>
</property>

<property>
  <name>dfs.block.size</name>
  <value>1073741824</value>
</property>

<property>
  <name>dfs.data.dir</name>
  <value>/grid/0/dev/yingyib/hadoop-rack345-1-data,/grid/1/dev/yingyib/hadoop-rack345-1-data,/grid/2/dev/yingyib/hadoop-rack345-1-data,/grid/3/dev/yingyib/hadoop-rack345-1-data</value>
</property>

</configuration>
```