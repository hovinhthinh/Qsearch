from __future__ import annotations

import json
import os
from typing import Sequence, List, Mapping

from quantulum3 import parser, load
from quantulum3.classes import Quantity as QQ

KGUNIT_COLLECTION_FILEPATH = os.path.join(os.path.dirname(__file__), 'kg-unit-collection.json')
QUANTULUM_NAME2KG_UNITS_FILEPATH = os.path.join(os.path.dirname(__file__), 'quantulum-name2kg-units.json')


class KBUnit(object):

    def __init__(
            self,
            entity: str,
            wd_entry: str,
            measured_concepts: Sequence[str],
            si_unit: str,
            conversion_to_si: float,
    ):
        self.entity = entity
        self.wd_entry = wd_entry
        self.measured_concepts = measured_concepts
        self.si_unit = si_unit
        self.conversion_to_si = conversion_to_si

    def __str__(self):
        return 'KBUnit{}'.format(str(self.__dict__))

    @staticmethod
    def load_unit_collection(path: str = KGUNIT_COLLECTION_FILEPATH) -> Mapping[str, KBUnit]:
        units = json.loads(open(path, 'r').read())
        return {o['entity']: KBUnit(o['entity'], o['wdEntry'], o['measuredConcepts'], o.get('siUnit'),
                                    o.get('conversionToSI')) for o
                in units}


class Quantity(object):
    def __init__(self, quantulum_quantity: QQ, kb_unit: KBUnit):
        self.quantulum_quantity = quantulum_quantity
        self._kb_unit = kb_unit

    @property
    def surface(self):
        return self.quantulum_quantity.surface

    @property
    def span(self):
        return self.quantulum_quantity.span

    @property
    def value(self):
        return self.quantulum_quantity.value

    @property
    def unit(self):
        return self.quantulum_quantity.unit

    @property
    def kb_unit(self) -> KBUnit:
        return self._kb_unit


# build mappings
_quantulum_name_2_KBUnit = {}
_kbunit_collection = KBUnit.load_unit_collection()

for k, v in _kbunit_collection.items():
    if '_wd:Q' in k:
        _quantulum_name_2_KBUnit[k[1:k.index('_wd:Q')].lower().replace('_', ' ')] = v
    else:
        _quantulum_name_2_KBUnit[k[1:-1].lower().replace('_', ' ')] = v

# load from manual mappings
for o in json.loads(open(QUANTULUM_NAME2KG_UNITS_FILEPATH, "r").read()):
    if len(o['KBUnits']) > 0:
        _quantulum_name_2_KBUnit[o['quantulumName']] = _kbunit_collection[o['KBUnits'][0]]


def parse(text: str) -> List[Quantity]:
    quants = []

    for q in parser.parse(text):
        quants.append(Quantity(q, _quantulum_name_2_KBUnit.get(q.unit.name)))
    return quants


if __name__ == '__main__':
    qts = parse("Gimme $1e10 now and also 1 TW and 0.5 J!")
    for u in qts:
        print(u.span, u.surface, u.value, u.unit, u.kb_unit)

    # quantulum_units = load.units()
    # for name, unit in quantulum_units.names.items():
    #     if name not in _quantulum_name_2_KBUnit:
    #         print(name + '\t' + unit.entity.name)
