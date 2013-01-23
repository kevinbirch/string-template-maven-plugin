foo(members, name, className) ::= <<
package com.example;

public class <className>
{
    <members:{each | private int <each>;
    }>

    private String name = "<name>";

    public <className>()
    {

    }

    public void doIt()
    {

    }
}
>>
