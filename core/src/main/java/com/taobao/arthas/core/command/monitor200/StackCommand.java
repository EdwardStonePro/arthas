package com.taobao.arthas.core.command.monitor200;

import com.taobao.arthas.core.GlobalOptions;
import com.taobao.arthas.core.advisor.AdviceListener;
import com.taobao.arthas.core.command.Constants;
import com.taobao.arthas.core.shell.command.CommandProcess;
import com.taobao.middleware.cli.annotations.Argument;
import com.taobao.middleware.cli.annotations.Description;
import com.taobao.middleware.cli.annotations.Name;
import com.taobao.middleware.cli.annotations.Option;
import com.taobao.middleware.cli.annotations.Summary;

/**
 * Jstack命令<br/>
 * 负责输出当前方法执行上下文
 *
 * @author vlinux
 * @author hengyunabc 2016-10-31
 */
@Name("stack")
@Summary("Display the stack trace for the specified class and method")
@Description(Constants.EXPRESS_DESCRIPTION + Constants.EXAMPLE +
        "  stack org.apache.commons.lang.StringUtils isBlank\n" +
        "  stack *StringUtils isBlank\n" +
        "  stack *StringUtils isBlank params[0].length==1\n" +
        "  stack *StringUtils isBlank '#cost>100'\n" +
        "  stack -E org\\.apache\\.commons\\.lang\\.StringUtils isBlank\n" +
        Constants.WIKI + Constants.WIKI_HOME + "stack")
public class StackCommand extends ClassPatternCommand {

    // method-pattern is optional for stack: allows matching all methods of a class
    @Override
    @Argument(index = 1, argName = "method-pattern", required = false)
    @Description("Method name pattern")
    public void setMethodPattern(String methodPattern) {
        super.setMethodPattern(methodPattern);
    }

    @Override
    @Option(shortName = "c", longName = "classloader")
    @Description("The hash code of the special class's classLoader")
    public void setHashCode(String hashCode) {
        super.setHashCode(hashCode);
    }

    @Override
    protected AdviceListener getAdviceListener(CommandProcess process) {
        return new StackAdviceListener(this, process, GlobalOptions.verbose || this.verbose);
    }
}
