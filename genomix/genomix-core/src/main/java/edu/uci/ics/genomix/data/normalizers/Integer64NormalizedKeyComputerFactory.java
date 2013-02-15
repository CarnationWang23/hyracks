package edu.uci.ics.genomix.data.normalizers;

import edu.uci.ics.hyracks.api.dataflow.value.INormalizedKeyComputer;
import edu.uci.ics.hyracks.api.dataflow.value.INormalizedKeyComputerFactory;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.Integer64SerializerDeserializer;

/**
 * Aggregation sort: speed up 
 * from hyracks
 *
 */
public class Integer64NormalizedKeyComputerFactory implements
		INormalizedKeyComputerFactory {
	private static final long serialVersionUID = 8735044913496854551L;

	@Override
	public INormalizedKeyComputer createNormalizedKeyComputer() {
		return new INormalizedKeyComputer() {
			private static final int POSTIVE_LONG_MASK = (3 << 30);
			private static final int NON_NEGATIVE_INT_MASK = (2 << 30);
			private static final int NEGATIVE_LONG_MASK = (0 << 30);

			@Override
			public int normalize(byte[] bytes, int start, int length) {
				long value = Integer64SerializerDeserializer.getLong(bytes,
						start);
				int highValue = (int) (value >> 32);
				if (highValue > 0) {
					/** * larger than Integer.MAX */
					int highNmk = getKey(highValue);
					highNmk >>= 2;
					highNmk |= POSTIVE_LONG_MASK;
					return highNmk;
				} else if (highValue == 0) {
					/** * smaller than Integer.MAX but >=0 */
					int lowNmk = (int) value;
					lowNmk >>= 2;
					lowNmk |= NON_NEGATIVE_INT_MASK;
					return lowNmk;
				} else {
					/** * less than 0: have not optimized for that */
					int highNmk = getKey(highValue);
					highNmk >>= 2;
					highNmk |= NEGATIVE_LONG_MASK;
					return highNmk;
				}
			}

			private int getKey(int value) {
				return value ^ Integer.MIN_VALUE;
			}
		};
	}
}
