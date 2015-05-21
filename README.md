This is a fork if the Awesome JavaMelody project: http://javamelody.googlecode.com

It adds the possibility of generating reports with a custom date range, down to the seconds

For example:

http://myJavamelodyApp/monitoring?custom.period=20/05/2015-18:10:22|20/05/2015-18:30:15

Will give you the reports between 18:10:22 and 18:30:15 of the 20/05/2015

The Time Axis of the graph is adjusted to plot labels every minute, in case the interval is less 20 minutes or less.

It is a workaround for performance tests monitoring. By workaround I actually mean, hack! There a tons of better ways of achieving this, but this is the least intrusive way I could find to implement it an be sure that nothing else is broken.

TODO: Add Date Format Pattern as a request parameter as well.

JavaMelody : monitoring of JavaEE applications
=========================

The goal of JavaMelody is to monitor Java or Java EE application servers in QA and production environments.

It is not a tool to simulate requests from users,
it is a tool to measure and calculate statistics on real operation of an application depending on the usage of the application by users.

JavaMelody is mainly based on statistics of requests and on evolution charts.
It allows to improve applications in QA and production and helps to:
- give facts about the average response times and number of executions
- make decisions when trends are bad, before problems become too serious
- optimize based on the more limiting response times
- find the root causes of response times
- verify the real improvement after optimizations 

See http://javamelody.googlecode.com

License ASL, http://www.apache.org/licenses/LICENSE-2.0
