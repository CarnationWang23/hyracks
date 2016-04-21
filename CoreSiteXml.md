
```
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>

<!-- Put site-specific property overrides in this file. -->

<configuration>

<property>
    <name>fs.default.name</name>
    <value>hdfs://xyz.com:31888</value>
</property>
<property>
    <name>hadoop.tmp.dir</name>
    <value>/grid/0/dev/vborkar/hadoop-rack345-1-tmp,/grid/1/dev/vborkar/hadoop-rack345-1-tmp,/grid/2/dev/vborkar/hadoop-rack345-1-tmp,/grid/3/dev/vborkar/hadoop-rack345-1-tmp</value>
</property>
<property>
    <name>io.sort.mb</name>
    <value>1524</value>
</property>

</configuration>
```