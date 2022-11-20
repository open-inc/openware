package de.openinc.ow.transformation;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import de.openinc.api.TransformationOperation;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;

public class FilterTransformer extends TransformationOperation {

	OpenWareDataItem res;

	@Override
	public TransformationOperation apply(User user, OpenWareDataItem old, JSONObject params) throws Exception {
		res = old.cloneItem(false);
		ExpressionParser parser = new SpelExpressionParser();
		String expression = params.getString("filterExpression");
		Expression exp = parser.parseExpression(expression);

		List<OpenWareValue> filtered = old.value().stream().parallel().filter(new Predicate<OpenWareValue>() {
			@Override
			public boolean test(OpenWareValue t) {
				Holder holder = new Holder();
				holder.val = t;
				try {
					return exp.getValue(holder, Boolean.class);
				} catch (EvaluationException e) {
					OpenWareInstance.getInstance()
							.logTrace("Filter Error: " + e.getMessage() + "\n" + e.getCause().toString());
					return false;
				}

			}
		}).collect(Collectors.toList());
		res.value().addAll(filtered);
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public OpenWareDataItem getResult() {
		// TODO Auto-generated method stub
		return res;

	}

	@Override
	public void setReference(String ref) {
		// TODO Auto-generated method stub

	}

}

class Holder {
	public OpenWareValue val;
}
