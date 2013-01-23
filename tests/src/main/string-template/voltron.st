voltron(members, robot, className, powerLevel) ::= <<
package com.example.application;

public class <className> implements Voltron
{
    <members:{each | private <robot> <each>;
    }>

    private String powerLevel = "<powerLevel>";

    public <className>()
    {

    }

    public void initiateDocking()
    {
        // ...
    }

    public void swordAttack()
    {
        // ...
    }

    public void beamAttack()
    {
        // ...
    }

    public void missileAttack()
    {
        // ...
    }
}
>>
