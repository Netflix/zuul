ruleset {
    ruleset 'rulesets/basic.xml'
    ruleset 'rulesets/braces.xml'
    ruleset('rulesets/dry.xml') {
        DuplicateNumberLiteral(enabled:false)
    }
    ruleset 'rulesets/groovyism.xml'
}
