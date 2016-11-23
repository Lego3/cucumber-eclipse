package cucumber.eclipse.steps.pydev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.python.pydev.core.IModule;
import org.python.pydev.core.IToken;
import org.python.pydev.core.MisconfigurationException;
import org.python.pydev.editor.codecompletion.revisited.modules.SourceToken;
import org.python.pydev.parser.jython.ast.FunctionDef;
import org.python.pydev.parser.jython.ast.Name;
import org.python.pydev.parser.jython.ast.Str;
import org.python.pydev.parser.jython.ast.decoratorsType;
import org.python.pydev.plugin.nature.PythonNature;

import cucumber.eclipse.steps.integration.IStepDefinitions;
import cucumber.eclipse.steps.integration.Step;

public class StepDefinitions implements IStepDefinitions {

	public static Set<Step> steps = null;
	public static List<String> availableSteps = Arrays.asList("given", "when", "then", "step");

	@Override
	public Set<Step> getSteps(IFile featurefile) {
		steps = new LinkedHashSet<Step>();

		IProject project = featurefile.getProject();
		try {
			if (project.isNatureEnabled("org.python.pydev.pythonNature")) {
				PythonNature nature = PythonNature.getPythonNature(project);
				for (IResource resource : project.members()) {
					if (resource.getType() == IResource.FOLDER) {
						scanPydevProjectForStepDefinitions((PythonNature)nature, (IFolder)resource);
					}
				}
			}
		} catch (CoreException e) {
			e.printStackTrace();
		} 

		return steps;
	}

	private void scanPydevProjectForStepDefinitions(PythonNature nature, IFolder folderToScan)
			throws CoreException {

		for (IResource resource : folderToScan.members()) {

			if (resource.getType() == IResource.FOLDER) {
				scanPydevProjectForStepDefinitions(nature, (IFolder)resource);
			}
			else if ("py".equals(resource.getFileExtension())) {
				String moduleName;
				try {
					moduleName = nature.resolveModule(resource);
				} catch (MisconfigurationException e) {
					continue;
				}
				if (moduleName == null) {
					continue;
				}
				IModule module = nature.getAstManager().getModule("testmodule", nature, true);
				if (module == null) {
					continue;
				}
				steps.addAll(getPydevCukeAnnotations(resource, module));
			}
		}
	}

	private List<Step> getPydevCukeAnnotations(IResource resource, IModule module) {
		List<Step> steps = new ArrayList<Step>();

		for (IToken token : module.getGlobalTokens()) {
			if (token.getType() == IToken.TYPE_FUNCTION && token instanceof SourceToken) {
				SourceToken realToken = (SourceToken)token;
				for (decoratorsType decorator : ((FunctionDef)realToken.getAst()).decs) {
					String decoratorName = ((Name)decorator.func).id;
					if (availableSteps.contains(decoratorName)) {
						Step step = new Step();
						step.setSource(resource);
						String actualStepText = ((Str)decorator.args[0]).s;
						// Convert python named matching groups to working Java regex
						actualStepText = actualStepText.replaceAll("\\(\\?P<.*>(.*)\\)", "$1");
						// Convert Behave parse expressions to working Java regex
						actualStepText = actualStepText.replaceAll("\\{.*\\}", ".*");
						step.setText(actualStepText);
						step.setLineNumber(decorator.beginLine);
						step.setLang("en");

						steps.add(step);
					}
				}
			}
		}
		return steps;
	}
}
