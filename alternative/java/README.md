# Contract Address Generator Java Version

Generate contract address by Java 17 program.

### Usage:

```
java -jar contract-address-generator.jar path/to/bytecode.txt
```

Download [contract-address-generator.jar](contract-address-generator.jar).

The text file contains bytecode as:

```
0x60806040526040518060400160405280600a81526020017f4361666520546f6b
656e000000000000000000000000000000000000000000008152506003908051
90602001906200005192919062000284565b5060405180604001604052806004
81526020017f4361666500000000000000000000000000000000000000000000
...
```

It calculats about 5m/s.
