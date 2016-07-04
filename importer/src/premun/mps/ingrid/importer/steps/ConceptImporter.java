package premun.mps.ingrid.importer.steps;

import org.jetbrains.mps.openapi.model.*;
import premun.mps.ingrid.parser.grammar.*;

import java.util.*;

/**
 * Import step that creates concepts, constraint data concepts and interface concepts for grammar rules.
 */
public class ConceptImporter extends ImportStep {

    @Override
    public void Execute() {
        // Creates constraint data concepts
        this.grammar.rules
            .values()
            .stream()
            .filter(r -> r instanceof RegexRule)
            .map(r -> (RegexRule) r)
            .forEach(this::importToken);

        // Creates interfaces for parser rules and concepts for alternatives
        this.grammar.rules
            .values()
            .stream()
            .filter(r -> r instanceof ParserRule)
            .map(r -> (ParserRule) r)
            .forEach(this::importRule);
    }

    /**
     * Imports parser rule as an interface or a concept (not their children or editors).
     *
     * For split rules, all alternatives are imported as empty concepts.
     * Example:
     * element:   '<' Name '>' Content '</' Name '>'
     *        |   '<' Name '/>'
     *        ;
     *
     * There will be created one interface and two concepts for this rule that will implements this concept.
     * They will be named element (interface) and element_1 and element_2 (concepts).
     *
     * @param rule Rule to be imported.
     */
    private void importRule(ParserRule rule) {
        if (this.isInterfaceNeeded(rule)) {
            // Generate unique name
            rule.name = this.namingService.generateName(rule.name);

            // We will create an interface and a child for each alternative that will inherit this interface
            SNode iface = this.nodeFactory.createInterface(rule.name, "Interfaces." + rule.name);
            this.structureModel.addRootNode(iface);
            rule.node = iface;

            // For each alternative, there will be a concept
            for (int i = 0; i < rule.alternatives.size(); ++i) {
                Alternative alternative = rule.alternatives.get(i);
                String name = this.namingService.generateName(rule.name + "_" + (i + 1));
                String description = alternative.comment != null ? alternative.comment : rule.name;

                // Concrete element, we can create a concept
                SNode concept = this.nodeFactory.createConcept(name, name, description, "Rules." + rule.name, rule.equals(this.grammar.rootRule));
                alternative.node = concept;

                this.structureModel.addRootNode(concept);
            }
        } else {
            // Generate unique name
            rule.name = this.namingService.generateName("I" + rule.name);

            // We will create plain old concept
            Alternative alternative = rule.alternatives.get(0);
            String description = alternative.comment != null ? alternative.comment : rule.name;

            // Not a rule that splits into more rules - we create it directly
            SNode concept = this.nodeFactory.createConcept(rule.name, rule.name, description, "Rules." + rule.name, rule.equals(this.grammar.rootRule));
            this.structureModel.addRootNode(concept);
            rule.node = concept;
            alternative.node = concept;
        }
    }

    /**
     * Imports a regex rule as a constraint data type element.
     *
     * @param rule Rule to be imported.
     */
    private void importToken(RegexRule rule) {
        rule.name = this.namingService.generateName(rule.name);
        SNode node = this.nodeFactory.createConstraintDataType(rule.name, rule.regexp, "Tokens");
        this.structureModel.addRootNode(node);
        rule.node = node;
    }

    /**
     * Detects, whether we need to create an interface for a rule.
     *
     * 1) When has more alternatives - then we need it to
     * 2) Or, when there could a shortcut lead through this concept. Then we need it, so that all end nodes can inherit it.
     *    Example situation:
     *    a  : a1
     *       | a2
     *       | a3
     *       ;
     *
     *    a2 : b1
     *       ;
     *
     *    b1 : C1
     *       | C2
     *       ;
     *
     *    Then, even when a2 is simple, we need an IA2 interface, so that nodes B1_1 and B1_2 can inherit it.
     *
     * @param rule Rule, to be verified
     * @return True, when we need to create an interface for this node.
     */
    private boolean isInterfaceNeeded(ParserRule rule) {
        // Case 1)
        if (rule.alternatives.size() > 1) {
            return true;
        }

        // Case 2)
        List<RuleReference> elements = rule.alternatives.get(0).elements;
        if (elements.size() == 1) {
            RuleReference element = elements.get(0);

            if (element.quantity == Quantity.EXACTLY_ONE && element.rule instanceof ParserRule) {
                return true;
            }
        }

        return false;
    }
}
